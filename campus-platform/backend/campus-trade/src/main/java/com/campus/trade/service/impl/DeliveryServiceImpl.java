package com.campus.trade.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.campus.common.exception.BusinessException;
import com.campus.trade.dto.DeliveryOrderVO;
import com.campus.trade.entity.DeliveryOrder;
import com.campus.trade.entity.Order;
import com.campus.trade.enums.DeliveryStatus;
import com.campus.trade.enums.OrderStatus;
import com.campus.trade.mapper.DeliveryOrderMapper;
import com.campus.trade.mapper.OrderMapper;
import com.campus.trade.service.DeliveryService;
import com.campus.trade.websocket.ChatWebSocketHandler;
import com.campus.user.entity.User;
import com.campus.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeliveryServiceImpl extends ServiceImpl<DeliveryOrderMapper, DeliveryOrder>
        implements DeliveryService {

    private final DeliveryOrderMapper deliveryMapper;
    private final OrderMapper orderMapper;
    private final UserMapper userMapper;
    private final ChatWebSocketHandler wsHandler;
    private final RedissonClient redisson;
    private final com.campus.user.service.AddressService addressService;

    @Override
    @Transactional
    public DeliveryOrderVO createDeliveryOrder(Long orderId) {
        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException("订单不存在");
        }

        DeliveryOrder delivery = new DeliveryOrder();
        delivery.setOrderId(orderId);
        delivery.setBuyerAddr(getUserDormitory(order.getBuyerId()));
        delivery.setSellerAddr(getUserDormitory(order.getSellerId()));
        delivery.setStatus(DeliveryStatus.PENDING.getCode());
        deliveryMapper.insert(delivery);

        // 订单保持 PENDING_DELIVERY 状态，等跑腿接单后再更新

        log.info("配送工单已创建: orderId={}", orderId);
        return toVO(delivery);
    }

    @Override
    public DeliveryOrderVO acceptOrder(Long runnerId, Long deliveryId) {
        // 校验交付员角色
        User runner = userMapper.selectById(runnerId);
        if (runner == null || (runner.getRole() != 2 && runner.getRole() != 1)) {
            throw new BusinessException(403, "只有交付员或管理员可以抢单");
        }

        String lockKey = "delivery:accept:" + deliveryId;
        RLock lock = redisson.getLock(lockKey);

        try {
            // 尝试获取锁，最多等待3秒
            if (!lock.tryLock(3, 10, java.util.concurrent.TimeUnit.SECONDS)) {
                throw new BusinessException("该工单正在被其他人抢单中");
            }

            try {
                DeliveryOrder delivery = getDeliveryById(deliveryId);
                if (delivery.getStatus() != DeliveryStatus.PENDING.getCode()) {
                    throw new BusinessException("工单已被接单");
                }

                delivery.setRunnerId(runnerId);
                delivery.setStatus(DeliveryStatus.ACCEPTED.getCode());
                delivery.setAcceptTime(LocalDateTime.now());
                deliveryMapper.updateById(delivery);

                // 更新主订单状态
                Order order = orderMapper.selectById(delivery.getOrderId());
                order.setStatus(OrderStatus.ASSIGNED.getCode());
                orderMapper.updateById(order);

                // 通知买卖双方
                notifyOrderUsers(order, "delivery_accepted",
                        "交付员已接单，正在前往卖家处取货");

                log.info("交付员接单: deliveryId={}, runnerId={}", deliveryId, runnerId);
                return toVO(delivery);
            } finally {
                lock.unlock();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException("系统异常，请稍后重试");
        }
    }

    @Override
    @Transactional
    public DeliveryOrderVO pickupGoods(Long runnerId, Long deliveryId, String photoUrl) {
        DeliveryOrder delivery = getDeliveryById(deliveryId);
        if (!delivery.getRunnerId().equals(runnerId)) {
            throw new BusinessException(403, "无权操作");
        }
        if (delivery.getStatus() != DeliveryStatus.ACCEPTED.getCode()) {
            throw new BusinessException("工单状态不正确");
        }

        delivery.setStatus(DeliveryStatus.PICKED_UP.getCode());
        delivery.setPickupPhoto(photoUrl);
        delivery.setPickupTime(LocalDateTime.now());
        deliveryMapper.updateById(delivery);

        // 更新主订单状态
        Order order = orderMapper.selectById(delivery.getOrderId());
        order.setStatus(OrderStatus.PICKED_UP.getCode());
        orderMapper.updateById(order);

        // 获取卖家地址用于取货
        if (delivery.getSellerAddr() == null) {
            delivery.setSellerAddr(getUserDormitory(order.getSellerId()));
            deliveryMapper.updateById(delivery);
        }

        notifyOrderUsers(order, "goods_picked_up",
                "交付员已取到商品，正在配送中");

        log.info("交付员取货: deliveryId={}", deliveryId);
        return toVO(delivery);
    }

    @Override
    @Transactional
    public DeliveryOrderVO deliverGoods(Long runnerId, Long deliveryId, String photoUrl) {
        DeliveryOrder delivery = getDeliveryById(deliveryId);
        if (!delivery.getRunnerId().equals(runnerId)) {
            throw new BusinessException(403, "无权操作");
        }
        if (delivery.getStatus() != DeliveryStatus.PICKED_UP.getCode()) {
            throw new BusinessException("工单状态不正确");
        }

        delivery.setStatus(DeliveryStatus.DELIVERED.getCode());
        delivery.setDeliverPhoto(photoUrl);
        delivery.setDeliverTime(LocalDateTime.now());
        deliveryMapper.updateById(delivery);

        // 更新主订单状态
        Order order = orderMapper.selectById(delivery.getOrderId());
        order.setStatus(OrderStatus.DELIVERED.getCode());
        orderMapper.updateById(order);

        // 通知买家确认收货
        wsHandler.pushToUser(order.getBuyerId(), Map.of(
                "type", "delivery_arrived",
                "data", Map.of(
                        "orderId", order.getId(),
                        "msg", "商品已送达！请当面验货后在APP点击确认收货，并将货款交给交付员"
                )
        ));

        log.info("商品已送达: deliveryId={}", deliveryId);
        return toVO(delivery);
    }

    @Override
    public List<DeliveryOrderVO> getPendingOrders() {
        List<DeliveryOrder> list = deliveryMapper.selectList(
                new LambdaQueryWrapper<DeliveryOrder>()
                        .eq(DeliveryOrder::getStatus, DeliveryStatus.PENDING.getCode())
                        .orderByDesc(DeliveryOrder::getCreateTime)
        );
        return list.stream().map(this::toVO).collect(Collectors.toList());
    }

    @Override
    public List<DeliveryOrderVO> getMyDeliveries(Long runnerId) {
        List<DeliveryOrder> list = deliveryMapper.selectList(
                new LambdaQueryWrapper<DeliveryOrder>()
                        .eq(DeliveryOrder::getRunnerId, runnerId)
                        .orderByDesc(DeliveryOrder::getCreateTime)
        );
        return list.stream().map(this::toVO).collect(Collectors.toList());
    }

    // ============ private helpers ============

    private DeliveryOrder getDeliveryById(Long id) {
        DeliveryOrder delivery = deliveryMapper.selectById(id);
        if (delivery == null) throw new BusinessException("工单不存在");
        return delivery;
    }

    private String getUserDormitory(Long userId) {
        User user = userMapper.selectById(userId);
        // 优先用宿舍地址
        if (user != null && user.getDormitory() != null && !user.getDormitory().isEmpty()) {
            return user.getDormitory();
        }
        // 没有则用默认收货地址
        try {
            com.campus.user.entity.Address addr = addressService.getDefaultAddress(userId);
            if (addr != null) {
                return addr.getBuilding() + " " + addr.getDetail() + " (" + addr.getContactName() + " " + addr.getPhone() + ")";
            }
        } catch (Exception ignored) {}
        return "未设置地址，请在个人中心或收货地址中添加";
    }

    private void notifyOrderUsers(Order order, String type, String msg) {
        wsHandler.pushToUser(order.getBuyerId(), Map.of("type", type, "data", Map.of("msg", msg)));
        wsHandler.pushToUser(order.getSellerId(), Map.of("type", type, "data", Map.of("msg", msg)));
    }

    private DeliveryOrderVO toVO(DeliveryOrder d) {
        DeliveryOrderVO vo = new DeliveryOrderVO();
        BeanUtil.copyProperties(d, vo);
        vo.setStatusDesc(DeliveryStatus.values()[d.getStatus()].getDesc());

        Order order = orderMapper.selectById(d.getOrderId());
        if (order != null) {
            vo.setOrderNo(order.getOrderNo());
        }
        if (d.getRunnerId() != null) {
            User runner = userMapper.selectById(d.getRunnerId());
            if (runner != null) vo.setRunnerNickname(runner.getNickname());
        }

        return vo;
    }
}
