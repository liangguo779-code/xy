package com.campus.trade.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.campus.common.exception.BusinessException;
import com.campus.common.service.NotificationService;
import com.campus.trade.delay.RedisDelayQueue;
import com.campus.trade.entity.DeliveryOrder;
import com.campus.trade.entity.Goods;
import com.campus.trade.entity.Order;
import com.campus.trade.enums.OrderStatus;
import com.campus.trade.mapper.DeliveryOrderMapper;
import com.campus.trade.mapper.GoodsMapper;
import com.campus.trade.mapper.OrderMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

/**
 * 订单超时处理：仅保留"送达后超时自动确认收货"一类延时任务，
 * 通过 {@link RedisDelayQueue} 在送达节点投递精确到期的任务。
 *
 * <p>任务语义：送达后超过 {@code trade.order.auto-confirm-receive-after}
 * 买家未确认收货则自动确认，交易完成。
 *
 * <p>订单状态流转出可处理范围时（如买家确认收货、人工取消），
 * 由业务代码调用 {@link #cancelPendingTasks} 移除已无意义的任务。
 * 处理器内部均使用条件更新做幂等保护，重复投递/重试不会产生副作用；
 * 多表善后在同一事务内完成，通知统一移到事务提交后发送。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderTimeoutService {

    /** 延时任务类型：送达超时自动确认收货 */
    public static final String TYPE_AUTO_CONFIRM_RECEIVE = "order_auto_confirm_receive";

    private final RedisDelayQueue delayQueue;
    private final OrderMapper orderMapper;
    private final GoodsMapper goodsMapper;
    private final DeliveryOrderMapper deliveryOrderMapper;
    private final GoodsIndexService goodsIndexService;
    private final NotificationService notificationService;
    private final RedissonClient redisson;
    private final ApplicationContext applicationContext;

    /** 送达后多久买家未确认则自动确认收货，默认 7 天 */
    @Value("${trade.order.auto-confirm-receive-after:7d}")
    private Duration autoConfirmReceiveAfter;

    /**
     * 启动时向延时队列注册任务处理器。
     *
     * <p>注意不能用 {@code this::method} 注册：方法引用绑定的是原始对象而非 Spring 代理，
     * 处理器内的 {@code @Transactional} 会因自调用而失效，多表善后将失去事务保护。
     * 因此延迟到任务执行时按类型取代理 Bean 再调用。
     */
    @PostConstruct
    void registerHandlers() {
        delayQueue.registerHandler(TYPE_AUTO_CONFIRM_RECEIVE, id -> self().autoConfirmReceive(id));
    }

    /** 取自身的 Spring 代理（CGLIB），经代理调用事务注解才会织入 */
    private OrderTimeoutService self() {
        return applicationContext.getBean(OrderTimeoutService.class);
    }

    // ── 任务投递 / 取消 ─────────────────────────────────────────────────────────

    /** 商品送达后投递"超时自动确认收货"任务。 */
    public void scheduleAutoConfirmReceive(Long orderId) {
        long executeAt = System.currentTimeMillis() + autoConfirmReceiveAfter.toMillis();
        delayQueue.schedule(TYPE_AUTO_CONFIRM_RECEIVE, orderId, executeAt);
        log.info("投递超时自动确认收货任务: orderId={}, 到期时间戳={}", orderId, executeAt);
    }

    /** 订单进入终态或状态流转出可处理范围时，移除未到期任务。 */
    public void cancelPendingTasks(Long orderId) {
        boolean cancelled = delayQueue.cancel(TYPE_AUTO_CONFIRM_RECEIVE, orderId);
        if (cancelled) {
            log.info("移除订单延时任务: orderId={}", orderId);
        }
    }

    // ── 任务处理器（幂等，可安全重试） ─────────────────────────────────────────

    /**
     * 超时自动确认收货：仅当订单仍处于"已送达待确认"才生效。
     * 与人工确认收货共用同一套完结逻辑：标记售出、通知双方评价。
     */
    @Transactional(rollbackFor = Exception.class)
    public void autoConfirmReceive(Long orderId) {
        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            return;
        }
        int updated = orderMapper.update(null, new LambdaUpdateWrapper<Order>()
                .eq(Order::getId, orderId)
                .eq(Order::getStatus, OrderStatus.DELIVERED.getCode())
                .set(Order::getStatus, OrderStatus.COMPLETED.getCode())
                .set(Order::getCompleteTime, LocalDateTime.now()));
        if (updated == 0) {
            log.info("订单已流转，跳过超时确认收货: orderId={}, status={}", orderId, order.getStatus());
            return;
        }

        // 商品标记已售出（仅从已预订状态转换，条件更新防竞态）
        goodsMapper.update(null, new LambdaUpdateWrapper<Goods>()
                .eq(Goods::getId, order.getGoodsId())
                .eq(Goods::getStatus, 3)
                .set(Goods::getStatus, 2));

        // 已售出不再展示：移出 ES 检索，提交后执行
        Long soldGoodsId = order.getGoodsId();
        runAfterCommit(() -> goodsIndexService.deleteGoods(soldGoodsId));

        // 通知统一移到事务提交后，避免回滚后用户收到"已确认收货"的过期通知
        DeliveryOrder deliveryOrder = deliveryOrderMapper.selectOne(
                new LambdaQueryWrapper<DeliveryOrder>().eq(DeliveryOrder::getOrderId, orderId));
        String msg = "订单送达后超时未确认，系统已自动确认收货（订单号: " + order.getOrderNo() + "）";
        Long runnerId = (deliveryOrder != null) ? deliveryOrder.getRunnerId() : null;
        runAfterCommit(() -> {
            notifyUser(order.getBuyerId(), "order_auto_completed", orderId, msg + "，请对卖家和交付员进行评价");
            notifyUser(order.getSellerId(), "order_auto_completed", orderId, msg + "，请对买家进行评价");
            if (runnerId != null) {
                notifyUser(runnerId, "order_auto_completed", orderId,
                        "订单超时未确认收货，系统已自动确认（订单号: " + order.getOrderNo() + "）");
            }
        });

        log.info("超时自动确认收货: orderId={}, orderNo={}", orderId, order.getOrderNo());
    }

    // ── 善后逻辑 ───────────────────────────────────────────────────────────────

    /**
     * 无其他进行中订单时恢复商品为在售，并回填 ES 索引。
     *
     * <p>必须持有与 {@code OrderServiceImpl.createOrder} 相同的 {@code lock:goods:{goodsId}} 分布式锁：
     * "先 count 活跃订单再回补"与并发下单的"插入订单+预订商品"若不互斥，
     * 回补可能把新预订商品（status=3）翻回在售（status=0），造成一货两卖（P0-3）。
     * 锁在事务提交/回滚后才释放（与 createOrder 一致），避免并发请求读到未提交的中间状态。
     *
     * <p>公开方法：人工取消订单（{@code OrderServiceImpl.cancelOrder}）复用同一套加锁回补逻辑。
     */
    public void restoreGoodsIfIdle(Order order) {
        RLock lock = redisson.getLock("lock:goods:" + order.getGoodsId());
        boolean locked = false;
        try {
            locked = lock.tryLock(3, 10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException("系统异常，请稍后重试");
        }
        if (!locked) {
            // 拿不到锁说明并发下单正在进行：抛错让事务回滚、任务按重试机制再补，绝不冒险回补
            throw new BusinessException("商品回补繁忙，请稍后重试");
        }

        boolean restored = false;
        try {
            // 持锁期间检查+回补是一个整体：新订单的"插入+预订"同样持锁，两边互斥后 count 不会漏掉在途订单
            Long otherActiveOrders = orderMapper.selectCount(
                    new LambdaQueryWrapper<Order>()
                            .eq(Order::getGoodsId, order.getGoodsId())
                            .ne(Order::getId, order.getId())
                            .ne(Order::getStatus, OrderStatus.COMPLETED.getCode())
                            .ne(Order::getStatus, OrderStatus.CANCELLED.getCode()));
            if (otherActiveOrders > 0) {
                return;
            }
            int updated = goodsMapper.update(null, new LambdaUpdateWrapper<Goods>()
                    .eq(Goods::getId, order.getGoodsId())
                    .eq(Goods::getStatus, 3)
                    .set(Goods::getStatus, 0)
                    .set(Goods::getReserveRef, null) // 清空占位持有者（P0-3 防护：恢复仅在持锁期间进行）
                    .set(Goods::getOffReason, null));
            restored = updated > 0;
        } finally {
            // 与 createOrder 相同：事务提交/回滚后再释放锁
            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCompletion(int status) {
                        if (lock.isHeldByCurrentThread()) {
                            lock.unlock();
                        }
                    }
                });
            } else if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }

        if (restored) {
            // ES 回填移出事务：提交后执行，ES 故障不影响回补一致性（索引可由 reindex 兜底）
            Long goodsId = order.getGoodsId();
            runAfterCommit(() -> {
                Goods restoredGoods = goodsMapper.selectById(goodsId);
                if (restoredGoods != null) {
                    goodsIndexService.indexGoods(restoredGoods);
                }
            });
        }
    }

    /** 事务提交后执行（无事务上下文时立即执行）：用于通知等旁路操作，回滚后不产生副作用 */
    private void runAfterCommit(Runnable task) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    runQuietly(task);
                }
            });
        } else {
            runQuietly(task);
        }
    }

    private void runQuietly(Runnable task) {
        try {
            task.run();
        } catch (Exception e) {
            log.warn("事务提交后回调执行失败", e);
        }
    }

    private void notifyUser(Long userId, String type, Long orderId, String msg) {
        try {
            notificationService.send(userId, type, "订单状态更新", msg,
                    "{\"orderId\":" + orderId + "}");
        } catch (Exception e) {
            log.warn("发送超时处理通知失败: userId={}, type={}", userId, type, e);
        }
    }
}
