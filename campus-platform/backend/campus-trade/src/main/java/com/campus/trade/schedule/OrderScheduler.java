package com.campus.trade.schedule;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.campus.trade.dto.HotTicketMessage;
import com.campus.trade.entity.Goods;
import com.campus.trade.entity.Order;
import com.campus.trade.enums.OrderStatus;
import com.campus.trade.mapper.GoodsMapper;
import com.campus.trade.mapper.OrderMapper;
import com.campus.trade.service.HotSaleOrderService;
import com.campus.trade.service.HotSaleStockService;
import com.campus.trade.service.HotTicketService;
import com.campus.trade.service.OrderTimeoutService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 兜底定时任务。
 *
 * <p>订单超时处理的主路径是 Redis 延时队列（送达时投递、按单精确到期，见
 * {@link com.campus.trade.delay.RedisDelayQueue} / {@link OrderTimeoutService}）。
 * 延时任务是 at-most-once 语义（ZREM 认领后进程崩溃任务即丢失），这里的扫描仅作兜底：
 * 防止延时任务丢失（如 Redis 故障/数据过期/宕机）导致订单永久悬挂。
 *
 * <p>超时窗口与延时队列共用同一份配置（{@code trade.order.auto-confirm-receive-after}），两处口径保持一致。
 * 热卖票据的超时释放走 pending 桶扫描（票据无 DB id，走不了延时队列），处理中转区另有对账。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderScheduler {

    private final OrderMapper orderMapper;
    private final GoodsMapper goodsMapper;
    private final OrderTimeoutService orderTimeoutService;
    private final HotSaleOrderService hotSaleOrderService;
    private final HotSaleStockService hotSaleStockService;
    private final HotTicketService hotTicketService;

    /** 送达后多久未确认收货则自动确认，与延时队列读取同一配置（默认 7 天） */
    @Value("${trade.order.auto-confirm-receive-after:7d}")
    private Duration autoConfirmReceiveAfter;

    /**
     * 每天凌晨 2 点 10 分兜底扫描：已送达(DELIVERED=9)且超时未确认收货的订单自动确认。
     * 复用 {@link OrderTimeoutService#autoConfirmReceive}（幂等），
     * 防止"超时自动确认收货"延时任务丢失后订单永久停留在待确认状态（P1-3）。
     */
    @Scheduled(cron = "0 10 2 * * ?")
    public void autoConfirmDeliveredOrders() {
        LocalDateTime deadline = LocalDateTime.now().minus(autoConfirmReceiveAfter);

        List<Order> expiredOrders = orderMapper.selectList(
                new LambdaQueryWrapper<Order>()
                        .eq(Order::getStatus, OrderStatus.DELIVERED.getCode())
                        .lt(Order::getUpdateTime, deadline)
        );

        if (expiredOrders.isEmpty()) {
            return;
        }

        for (Order order : expiredOrders) {
            orderTimeoutService.autoConfirmReceive(order.getId());
        }

        log.info("兜底定时任务: 自动确认收货 {} 个超时订单", expiredOrders.size());
    }

    /**
     * 热卖票据超时释放：扫各商品 pending 桶，比对消息内 expireAt（JVM 时钟，与写消息同源）。
     * 这是超时释放的<b>主路径</b>——票据无 DB id 走不了延时队列。
     * 释放幂等，重复执行无副作用；已确认票据不在 pending 桶，不会被误释放。
     */
    @Scheduled(fixedDelayString = "${trade.hot-order.release-scan-ms:10000}")
    public void releaseExpiredHotTickets() {
        long now = System.currentTimeMillis();
        int released = 0;
        for (HotTicketMessage message : hotTicketService.listAllPending()) {
            if (message.getExpireAt() == null || message.getExpireAt() >= now) {
                continue;
            }
            try {
                hotSaleOrderService.releaseTicket(message, "timeout");
                released++;
            } catch (Exception e) {
                log.warn("热卖票据超时释放失败: orderNo={}", message.getOrderNo(), e);
            }
        }
        if (released > 0) {
            log.info("热卖票据超时释放: {} 张", released);
        }

        // legacy 分支（下一版删）：部署瞬间的存量 status=10 DB 行（老模型遗留），置 4 取消
        List<Order> legacyExpired = orderMapper.selectList(
                new LambdaQueryWrapper<Order>()
                        .eq(Order::getStatus, OrderStatus.HOT_PENDING_CONFIRM.getCode())
                        .lt(Order::getExpireTime, LocalDateTime.now())
        );
        for (Order order : legacyExpired) {
            try {
                orderMapper.update(null, new LambdaUpdateWrapper<Order>()
                        .eq(Order::getId, order.getId())
                        .eq(Order::getStatus, OrderStatus.HOT_PENDING_CONFIRM.getCode())
                        .set(Order::getStatus, OrderStatus.CANCELLED.getCode())
                        .set(Order::getUpdateTime, LocalDateTime.now()));
                log.info("legacy 热卖订单超时取消: orderNo={}", order.getOrderNo());
            } catch (Exception e) {
                log.warn("legacy 热卖订单取消失败: orderNo={}", order.getOrderNo(), e);
            }
        }
    }

    /**
     * 热卖处理中转区对账：worker 崩溃残留的清理（有 DB 行→摘除；无→重投已确认队列重试）。
     * 见 {@link HotSaleOrderService#reconcileProcessingTickets}。
     */
    @Scheduled(fixedDelayString = "${trade.hot-order.reconcile-processing-ms:30000}")
    public void reconcileHotProcessingTickets() {
        try {
            hotSaleOrderService.reconcileProcessingTickets();
        } catch (Exception e) {
            log.warn("热卖处理中转区对账失败", e);
        }

        // 积压观测：已确认队列过长说明 worker/DB 可能有问题
        long confirmedLen = hotSaleStockService.confirmedQueueLength();
        if (confirmedLen > 100) {
            log.warn("热卖已确认队列积压: len={}", confirmedLen);
        }
    }

    /**
     * 每天凌晨 3 点执行：自动下架超过 30 天的在售商品
     * 判断逻辑：优先用 refreshTime（最近刷新时间），没有则用 createTime（发布时间）
     */
    @Scheduled(cron = "0 0 3 * * ?")
    public void autoDelistExpiredGoods() {
        LocalDateTime deadline = LocalDateTime.now().minusDays(30);

        // 查询所有在售商品，然后在代码中判断是否超期（热卖商品除外：生命周期由管理员控制）
        List<Goods> onSaleGoods = goodsMapper.selectList(
                new LambdaQueryWrapper<Goods>()
                        .eq(Goods::getStatus, 0)
                        .ne(Goods::getGoodsType, 1)
        );

        int count = 0;
        for (Goods goods : onSaleGoods) {
            // 用 refreshTime 判断，没有则用 createTime
            LocalDateTime lastActive = goods.getRefreshTime() != null
                    ? goods.getRefreshTime() : goods.getCreateTime();
            if (lastActive != null && lastActive.isBefore(deadline)) {
                // 条件更新防 check-then-act 竞态：仅当商品仍在售才下架，
                // 防止扫描期间商品被下单预订（0→3）后被误下架
                int updated = goodsMapper.update(null, new LambdaUpdateWrapper<Goods>()
                        .eq(Goods::getId, goods.getId())
                        .eq(Goods::getStatus, 0)
                        .set(Goods::getStatus, 1)   // 1-下架
                        .set(Goods::getOffReason, "超过30天未刷新，系统自动下架"));
                if (updated == 0) {
                    // 状态已变化（如已被预订/售出），跳过
                    continue;
                }
                count++;
                log.info("自动下架过期商品: goodsId={}, title={}, 最后活跃时间={}",
                        goods.getId(), goods.getTitle(), lastActive);
            }
        }

        if (count > 0) {
            log.info("定时任务: 自动下架 {} 个超过30天的过期商品", count);
        }
    }
}
