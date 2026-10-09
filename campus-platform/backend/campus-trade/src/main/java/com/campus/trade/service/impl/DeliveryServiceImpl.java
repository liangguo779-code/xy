package com.campus.trade.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
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
import com.campus.trade.service.DeliveryService;
import com.campus.trade.service.DeliveryTrackService;
import com.campus.trade.service.OrderTimeoutService;
import com.campus.trade.websocket.ChatWebSocketHandler;
import com.campus.common.entity.User;
import com.campus.common.entity.Address;
import com.campus.common.mapper.UserMapper;
import com.campus.common.mapper.AddressMapper;
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

/** 跑腿配送服务实现，负责配送工单全流程与主订单状态联动、WebSocket 通知 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeliveryServiceImpl extends ServiceImpl<DeliveryOrderMapper, DeliveryOrder>
        implements DeliveryService {

    private final DeliveryOrderMapper deliveryMapper;
    private final DeliveryTrackMapper trackMapper;
    private final OrderMapper orderMapper;
    private final UserMapper userMapper;
    private final AddressMapper addressMapper;
    private final ChatWebSocketHandler wsHandler;
    private final RedissonClient redisson;
    private final DeliveryTrackService deliveryTrackService;
    private final OrderTimeoutService orderTimeoutService;

    /** 创建配送工单，买卖家地址取宿舍/默认收货地址，同一订单不可重复创建 */
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
        deliveryMapper.insert(delivery);

        // 订单保持 PENDING_DELIVERY 状态，等跑腿接单后再更新

        log.info("配送工单已创建: orderId={}", orderId);
        return toVO(delivery);
    }

    /** 交付员抢单：Redisson 分布式锁防并发抢单，锁到事务提交后才释放 */
    @Override
    @Transactional
    public DeliveryOrderVO acceptOrder(Long runnerId, Long deliveryId,
                                       java.math.BigDecimal lat, java.math.BigDecimal lng) {
        // 校验交付员角色
        User runnerUser = userMapper.selectById(runnerId);
        if (runnerUser == null) {
            throw new BusinessException(403, "用户不存在");
        }
        if (runnerUser.getRole() != null && runnerUser.getRole() != 2 && runnerUser.getRole() != 1) {
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

                // 条件更新防抢单/取消竞态：仅当工单仍为待接单才可抢单（P0-4）
                LocalDateTime acceptTime = LocalDateTime.now();
                int updated = deliveryMapper.update(null, new LambdaUpdateWrapper<DeliveryOrder>()
                        .eq(DeliveryOrder::getId, deliveryId)
                        .eq(DeliveryOrder::getStatus, DeliveryStatus.PENDING.getCode())
                        .set(DeliveryOrder::getRunnerId, runnerId)
                        .set(DeliveryOrder::getStatus, DeliveryStatus.ACCEPTED.getCode())
                        .set(DeliveryOrder::getAcceptTime, acceptTime));
                if (updated == 0) {
                    throw new BusinessException("工单已被接单或已取消，状态已变化");
                }
                delivery.setRunnerId(runnerId);
                delivery.setStatus(DeliveryStatus.ACCEPTED.getCode());
                delivery.setAcceptTime(acceptTime);

                // 更新主订单状态（条件更新带前置状态谓词：仅当仍为待派单才流转为已派单，
                // 防止 updateById 全量覆盖把并发取消的 CANCELLED 击穿为 ASSIGNED，P0-4）
                Order order = orderMapper.selectById(delivery.getOrderId());
                if (order == null) throw new BusinessException("关联订单不存在");
                int orderUpdated = orderMapper.update(null, new LambdaUpdateWrapper<Order>()
                        .eq(Order::getId, order.getId())
                        .eq(Order::getStatus, OrderStatus.PENDING_DELIVERY.getCode())
                        .set(Order::getStatus, OrderStatus.ASSIGNED.getCode())
                        .set(Order::getUpdateTime, LocalDateTime.now()));
                if (orderUpdated == 0) {
                    throw new BusinessException("订单状态已变化，请刷新后重试");
                }
                order.setStatus(OrderStatus.ASSIGNED.getCode());


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

    /** 交付员取货并上传拍照存证，联动主订单为已取货状态 */
    @Override
    @Transactional
    public DeliveryOrderVO pickupGoods(Long runnerId, Long deliveryId, String photoUrl,
                                       java.math.BigDecimal lat, java.math.BigDecimal lng) {
        DeliveryOrder delivery = getDeliveryById(deliveryId);
        if (!delivery.getRunnerId().equals(runnerId)) {
            throw new BusinessException(403, "无权操作");
        }

        // 条件更新防状态竞态：仅当工单仍为已接单才允许取货，失败说明状态已被其他线程改变
        LocalDateTime pickupTime = LocalDateTime.now();
        int updated = deliveryMapper.update(null, new LambdaUpdateWrapper<DeliveryOrder>()
                .eq(DeliveryOrder::getId, deliveryId)
                .eq(DeliveryOrder::getStatus, DeliveryStatus.ACCEPTED.getCode())
                .set(DeliveryOrder::getStatus, DeliveryStatus.PICKED_UP.getCode())
                .set(DeliveryOrder::getPickupPhoto, photoUrl)
                .set(DeliveryOrder::getPickupTime, pickupTime));
        if (updated == 0) {
            throw new BusinessException("状态已变化，请刷新后重试");
        }
        delivery.setStatus(DeliveryStatus.PICKED_UP.getCode());
        delivery.setPickupPhoto(photoUrl);
        delivery.setPickupTime(pickupTime);

        // 更新主订单状态（条件更新：仅当仍为已派单才流转为已取货）
        Order order = orderMapper.selectById(delivery.getOrderId());
        if (order == null) throw new BusinessException("关联订单不存在");
        int orderUpdated = orderMapper.update(null, new LambdaUpdateWrapper<Order>()
                .eq(Order::getId, order.getId())
                .eq(Order::getStatus, OrderStatus.ASSIGNED.getCode())
                .set(Order::getStatus, OrderStatus.PICKED_UP.getCode())
                .set(Order::getUpdateTime, LocalDateTime.now()));
        if (orderUpdated == 0) {
            throw new BusinessException("状态已变化，请刷新后重试");
        }
        order.setStatus(OrderStatus.PICKED_UP.getCode());

        // 获取卖家地址用于取货（定点更新，避免整行覆盖冲掉刚流转的状态）
        if (delivery.getSellerAddr() == null) {
            String sellerAddr = getUserDormitory(order.getSellerId());
            delivery.setSellerAddr(sellerAddr);
            deliveryMapper.update(null, new LambdaUpdateWrapper<DeliveryOrder>()
                    .eq(DeliveryOrder::getId, deliveryId)
                    .set(DeliveryOrder::getSellerAddr, sellerAddr));
        }

        notifyOrderUsers(order, "goods_picked_up",
                "交付员已取到商品，正在配送中");

        // 记录取货轨迹（同一事务内）
        saveTrack(deliveryId, runnerId, "pickup", lat, lng, null, photoUrl);

        log.info("交付员取货: deliveryId={}", deliveryId);
        return toVO(delivery);
    }

    /** 交付员送达并上传拍照存证，通知买家确认收货 */
    @Override
    @Transactional
    public DeliveryOrderVO deliverGoods(Long runnerId, Long deliveryId, String photoUrl,
                                        java.math.BigDecimal lat, java.math.BigDecimal lng) {
        DeliveryOrder delivery = getDeliveryById(deliveryId);
        if (!delivery.getRunnerId().equals(runnerId)) {
            throw new BusinessException(403, "无权操作");
        }

        // 条件更新防状态竞态：仅当工单仍为已取货才允许送达，失败说明状态已被其他线程改变
        LocalDateTime deliverTime = LocalDateTime.now();
        int updated = deliveryMapper.update(null, new LambdaUpdateWrapper<DeliveryOrder>()
                .eq(DeliveryOrder::getId, deliveryId)
                .eq(DeliveryOrder::getStatus, DeliveryStatus.PICKED_UP.getCode())
                .set(DeliveryOrder::getStatus, DeliveryStatus.DELIVERED.getCode())
                .set(DeliveryOrder::getDeliverPhoto, photoUrl)
                .set(DeliveryOrder::getDeliverTime, deliverTime));
        if (updated == 0) {
            throw new BusinessException("状态已变化，请刷新后重试");
        }
        delivery.setStatus(DeliveryStatus.DELIVERED.getCode());
        delivery.setDeliverPhoto(photoUrl);
        delivery.setDeliverTime(deliverTime);

        // 更新主订单状态（条件更新：仅当仍为已取货才流转为已送达）
        Order order = orderMapper.selectById(delivery.getOrderId());
        if (order == null) throw new BusinessException("关联订单不存在");
        int orderUpdated = orderMapper.update(null, new LambdaUpdateWrapper<Order>()
                .eq(Order::getId, order.getId())
                .eq(Order::getStatus, OrderStatus.PICKED_UP.getCode())
                .set(Order::getStatus, OrderStatus.DELIVERED.getCode())
                .set(Order::getUpdateTime, LocalDateTime.now()));
        if (orderUpdated == 0) {
            throw new BusinessException("状态已变化，请刷新后重试");
        }
        order.setStatus(OrderStatus.DELIVERED.getCode());

        // 商品已送达：移除"超时未送达"等旧任务，投递超时自动确认收货任务（须在状态条件更新成功之后）
        orderTimeoutService.cancelPendingTasks(order.getId());
        orderTimeoutService.scheduleAutoConfirmReceive(order.getId());

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

    /** 派单大厅：按创建时间倒序返回待接单工单 */
    @Override
    public List<DeliveryOrderVO> getPendingOrders() {
        List<DeliveryOrder> list = deliveryMapper.selectList(
                new LambdaQueryWrapper<DeliveryOrder>()
                        .eq(DeliveryOrder::getStatus, DeliveryStatus.PENDING.getCode())
                        .orderByDesc(DeliveryOrder::getCreateTime)
        );
        return list.stream().map(this::toVO).collect(Collectors.toList());
    }

    /** 我的工单列表（交付员视角），按创建时间倒序 */
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
            User user = userMapper.selectById(userId);
            if (user != null && user.getDormitory() != null && !user.getDormitory().isEmpty()) {
                return user.getDormitory();
            }
        } catch (Exception e) {
            log.warn("查询用户信息失败: userId={}", userId, e);
        }
        // 没有则用默认收货地址
        try {
            Address address = addressMapper.selectOne(
                    new LambdaQueryWrapper<Address>()
                            .eq(Address::getUserId, userId)
                            .eq(Address::getIsDefault, 1)
                            .last("LIMIT 1"));
            if (address != null) {
                return address.getBuilding() + " " + address.getDetail() + " (" + address.getContactName() + " " + address.getPhone() + ")";
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
                User runner = userMapper.selectById(d.getRunnerId());
                if (runner != null) {
                    vo.setRunnerNickname(runner.getNickname());
                }
            } catch (Exception e) {
                // 查询失败时忽略
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
