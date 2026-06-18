package com.campus.trade.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.campus.common.exception.BusinessException;
import com.campus.trade.dto.DeliveryOrderVO;
import com.campus.trade.entity.DeliveryOrder;
import com.campus.trade.entity.DeliveryTrack;
import com.campus.trade.entity.Order;
import com.campus.trade.enums.DeliveryStatus;
import com.campus.trade.enums.OrderStatus;
import com.campus.trade.mapper.DeliveryOrderMapper;
import com.campus.trade.mapper.DeliveryTrackMapper;
import com.campus.trade.mapper.OrderMapper;
import com.campus.trade.service.DeliveryFeeService;
import com.campus.trade.service.DeliveryService;
import com.campus.trade.service.DeliveryTrackService;
import com.campus.trade.websocket.ChatWebSocketHandler;
import com.campus.user.feign.UserFeignClient;
import com.campus.user.feign.dto.UserVO;
import com.campus.user.feign.dto.AddressVO;
import com.campus.common.result.R;
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
    private final DeliveryTrackMapper trackMapper;
    private final OrderMapper orderMapper;
    private final UserFeignClient userFeignClient;
    private final ChatWebSocketHandler wsHandler;
    private final RedissonClient redisson;
    private final DeliveryTrackService deliveryTrackService;
    private final DeliveryFeeService deliveryFeeService;

    @Override
    @Transactional
    public DeliveryOrderVO createDeliveryOrder(Long orderId) {
        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException("订单不存在");
        }

        // 防止重复创建配送工单
        Long existing = deliveryMapper.selectCount(
                new LambdaQueryWrapper<DeliveryOrder>().eq(DeliveryOrder::getOrderId, orderId));
        if (existing > 0) {
            throw new BusinessException("该订单已有配送工单");
        }

        DeliveryOrder delivery = new DeliveryOrder();
        delivery.setOrderId(orderId);
        delivery.setBuyerAddr(getUserDormitory(order.getBuyerId()));
        delivery.setSellerAddr(getUserDormitory(order.getSellerId()));
        delivery.setStatus(DeliveryStatus.PENDING.getCode());
        // 保存楼层信息并计算配送费
        int floor = order.getFloor() != null ? order.getFloor() : 1;
        boolean hasElevator = order.getHasElevator() == null || order.getHasElevator() == 1;
        delivery.setFloor(floor);
        delivery.setHasElevator(hasElevator ? 1 : 0);
        delivery.setDeliveryFee(deliveryFeeService.calculateFee(floor, hasElevator));
        deliveryMapper.insert(delivery);

        // 订单保持 PENDING_DELIVERY 状态，等跑腿接单后再更新

        log.info("配送工单已创建: orderId={}", orderId);
        return toVO(delivery);
    }

    @Override
    @Transactional
    public DeliveryOrderVO acceptOrder(Long runnerId, Long deliveryId,
                                       java.math.BigDecimal lat, java.math.BigDecimal lng) {
        // 校验交付员角色
        UserVO runner;
        try {
            R<UserVO> runnerResult = userFeignClient.getUserById(runnerId);
            if (runnerResult.getCode() != 200 || runnerResult.getData() == null) {
                throw new BusinessException(403, "用户不存在");
            }
            runner = runnerResult.getData();
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(403, "用户服务不可用，请稍后重试");
        }
        if (runner.getRole() != 2 && runner.getRole() != 1) {
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
                if (order == null) throw new BusinessException("关联订单不存在");
                order.setStatus(OrderStatus.ASSIGNED.getCode());
                orderMapper.updateById(order);

                // 通知买卖双方
                notifyOrderUsers(order, "delivery_accepted",
                        "交付员已接单，正在前往卖家处取货");

                // 记录接单轨迹（同一事务内）
                saveTrack(deliveryId, runnerId, "accept", lat, lng, null, null);

                log.info("交付员接单: deliveryId={}, runnerId={}", deliveryId, runnerId);

                // 事务提交后再释放锁，防止并发骑手读到旧状态
                DeliveryOrderVO result = toVO(delivery);
                org.springframework.transaction.support.TransactionSynchronizationManager
                        .registerSynchronization(new org.springframework.transaction.support.TransactionSynchronization() {
                            @Override
                            public void afterCompletion(int status) {
                                try {
                                    if (lock.isHeldByCurrentThread()) {
                                        lock.unlock();
                                    }
                                } catch (Exception e) {
                                    log.error("释放抢单锁失败", e);
                                }
                            }
                        });
                return result;
            } catch (Exception e) {
                // 业务异常时立即释放锁
                try {
                    if (lock.isHeldByCurrentThread()) {
                        lock.unlock();
                    }
                } catch (Exception ignored) {}
                throw e;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException("系统异常，请稍后重试");
        }
    }

    @Override
    @Transactional
    public DeliveryOrderVO pickupGoods(Long runnerId, Long deliveryId, String photoUrl,
                                       java.math.BigDecimal lat, java.math.BigDecimal lng) {
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
        if (order == null) throw new BusinessException("关联订单不存在");
        order.setStatus(OrderStatus.PICKED_UP.getCode());
        orderMapper.updateById(order);

        // 获取卖家地址用于取货
        if (delivery.getSellerAddr() == null) {
            delivery.setSellerAddr(getUserDormitory(order.getSellerId()));
            deliveryMapper.updateById(delivery);
        }

        notifyOrderUsers(order, "goods_picked_up",
                "交付员已取到商品，正在配送中");

        // 记录取货轨迹（同一事务内）
        saveTrack(deliveryId, runnerId, "pickup", lat, lng, null, photoUrl);

        log.info("交付员取货: deliveryId={}", deliveryId);
        return toVO(delivery);
    }

    @Override
    @Transactional
    public DeliveryOrderVO deliverGoods(Long runnerId, Long deliveryId, String photoUrl,
                                        java.math.BigDecimal lat, java.math.BigDecimal lng) {
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
        if (order == null) throw new BusinessException("关联订单不存在");
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
        // 通知卖家配送已完成
        wsHandler.pushToUser(order.getSellerId(), Map.of(
                "type", "delivery_arrived",
                "data", Map.of(
                        "orderId", order.getId(),
                        "msg", "商品已送达买家处，等待买家确认收货"
                )
        ));

        // 记录送达轨迹（同一事务内）
        saveTrack(deliveryId, runnerId, "deliver", lat, lng, null, photoUrl);

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
        // 优先用宿舍地址
        try {
            R<UserVO> userResult = userFeignClient.getUserById(userId);
            if (userResult.getCode() == 200 && userResult.getData() != null) {
                UserVO user = userResult.getData();
                if (user.getDormitory() != null && !user.getDormitory().isEmpty()) {
                    return user.getDormitory();
                }
            }
        } catch (Exception e) {
            log.warn("查询用户信息失败: userId={}", userId, e);
        }
        // 没有则用默认收货地址
        try {
            R<AddressVO> addrResult = userFeignClient.getDefaultAddress(userId);
            if (addrResult.getCode() == 200 && addrResult.getData() != null) {
                AddressVO addr = addrResult.getData();
                return addr.getBuilding() + " " + addr.getDetail() + " (" + addr.getContactName() + " " + addr.getPhone() + ")";
            }
        } catch (Exception e) {
            log.warn("查询用户默认地址失败: userId={}", userId, e);
        }
        return "未设置地址，请在个人中心或收货地址中添加";
    }

    private void notifyOrderUsers(Order order, String type, String msg) {
        wsHandler.pushToUser(order.getBuyerId(), Map.of("type", type, "data", Map.of("msg", msg)));
        wsHandler.pushToUser(order.getSellerId(), Map.of("type", type, "data", Map.of("msg", msg)));
    }

    private DeliveryOrderVO toVO(DeliveryOrder d) {
        DeliveryOrderVO vo = new DeliveryOrderVO();
        BeanUtil.copyProperties(d, vo);
        // 安全查找状态描述，防止数组越界
        String statusDesc = "未知状态";
        for (DeliveryStatus ds : DeliveryStatus.values()) {
            if (ds.getCode() == d.getStatus()) {
                statusDesc = ds.getDesc();
                break;
            }
        }
        vo.setStatusDesc(statusDesc);

        Order order = orderMapper.selectById(d.getOrderId());
        if (order != null) {
            vo.setOrderNo(order.getOrderNo());
        }
        if (d.getRunnerId() != null) {
            try {
                R<UserVO> runnerResult = userFeignClient.getUserById(d.getRunnerId());
                if (runnerResult.getCode() == 200 && runnerResult.getData() != null) {
                    vo.setRunnerNickname(runnerResult.getData().getNickname());
                }
            } catch (Exception e) {
                // Feign 调用失败时忽略
            }
        }

        return vo;
    }

    /** 内部方法：记录配送轨迹（在同一事务内调用） */
    private void saveTrack(Long deliveryId, Long runnerId, String action,
                           java.math.BigDecimal lat, java.math.BigDecimal lng,
                           String address, String photoUrl) {
        DeliveryTrack track = new DeliveryTrack();
        track.setDeliveryId(deliveryId);
        track.setRunnerId(runnerId);
        track.setAction(action);
        track.setLatitude(lat);
        track.setLongitude(lng);
        track.setAddress(address);
        track.setPhotoUrl(photoUrl);
        trackMapper.insert(track);
    }
}
