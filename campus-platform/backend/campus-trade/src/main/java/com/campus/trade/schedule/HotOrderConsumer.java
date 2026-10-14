package com.campus.trade.schedule;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.campus.common.service.NotificationService;
import com.campus.trade.dto.HotTicketMessage;
import com.campus.trade.entity.DeliveryOrder;
import com.campus.trade.entity.Goods;
import com.campus.trade.entity.Order;
import com.campus.trade.enums.OrderStatus;
import com.campus.trade.mapper.DeliveryOrderMapper;
import com.campus.trade.mapper.GoodsMapper;
import com.campus.trade.mapper.OrderMapper;
import com.campus.trade.service.DeliveryService;
import com.campus.trade.service.HotSaleStockService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 热卖订单消费者：轮询已确认队列，消费消息时才把订单落进 MySQL。
 *
 * <p><b>订单的「产生」= 本类消费 list 的时刻</b>。下单/确认阶段 Redis 里只有消息；
 * 这里认领（LPOP confirmed → RPUSH processing）后在一个事务里完成
 * 「插入订单 + 账本扣库存/累销量 + 建配送工单」，<b>提交成功后才从 processing 摘除消息</b>。
 *
 * <p><b>LREM 绝不能写进 {@code @Transactional} 方法体</b>：Redis 不随 MySQL 回滚——
 * 事务内摘消息、随后回滚 = 消息没了、订单也没了，票据彻底丢失。
 * 崩溃窗口两段都可恢复，没有丢单路径：
 * <ul>
 *   <li>认领后、落库前崩 → processing 残留且无 DB 行 → {@code reconcileProcessingTickets} 重投已确认队列；</li>
 *   <li>落库后、LREM 前崩 → processing 残留且有 DB 行 → 对账按 orderNo 查到即摘（幂等）。</li>
 * </ul>
 *
 * <p>幂等三重保障：入参前 {@code selectCount(orderNo)} 短路 → {@code UNIQUE(order_no)} 兜底
 * （并发 worker 抛 {@link DuplicateKeyException} 视为对方已成功）→ processing 摘除本身幂等。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HotOrderConsumer {

    private final HotSaleStockService stockService;
    private final OrderMapper orderMapper;
    private final GoodsMapper goodsMapper;
    private final DeliveryOrderMapper deliveryOrderMapper;
    private final DeliveryService deliveryService;
    private final NotificationService notificationService;
    private final ApplicationContext applicationContext;

    /** 单飞：上一轮未跑完不重入（照抄 EsOutboxService 的轮询约定） */
    private final AtomicBoolean polling = new AtomicBoolean(false);

    @Value("${trade.hot-order.consume-batch-size:20}")
    private int batchSize;

    /** 轮询消费已确认队列（秒级，前端「处理中」窗口由此决定） */
    @Scheduled(fixedDelayString = "${trade.hot-order.consume-poll-ms:200}")
    public void poll() {
        if (!polling.compareAndSet(false, true)) {
            return;
        }
        try {
            for (int i = 0; i < batchSize; i++) {
                String member = stockService.claimConfirmed();
                if (member == null) {
                    break;
                }
                processOne(member);
            }
        } catch (Exception e) {
            log.warn("热卖订单消费轮询异常", e);
        } finally {
            polling.set(false);
        }
    }

    /** 单条消息处理：幂等短路 / 落库 / 失败留给对账重投 */
    private void processOne(String member) {
        HotTicketMessage message = stockService.parseMember(member);
        if (message == null) {
            stockService.removeProcessing(member);
            log.warn("丢弃非法热卖消息: {}", member);
            return;
        }

        // 幂等短路：落库成功但 afterCommit LREM 未执行的残留，直接补摘
        if (orderMapper.selectCount(new LambdaQueryWrapper<Order>()
                .eq(Order::getOrderNo, message.getOrderNo())) > 0) {
            stockService.finishProcessing(member);
            log.info("热卖订单已落库，清理处理中残留: orderNo={}", message.getOrderNo());
            return;
        }

        try {
            // 经 Spring 代理调用，@Transactional 织入
            self().materialize(member, message);
        } catch (DuplicateKeyException e) {
            // 并发 worker 抢先落库（UNIQUE(order_no) 兜底），视为成功
            stockService.finishProcessing(member);
            log.info("热卖订单并发落库冲突，视为成功: orderNo={}", message.getOrderNo());
        } catch (Exception e) {
            // 消息留在 processing，交由 reconcileProcessingTickets 重投
            log.warn("热卖订单落库失败(交由对账重试): orderNo={}", message.getOrderNo(), e);
        }
    }

    /**
     * 落库核心：订单行 + 账本扣减 + 配送工单，一个事务内原子提交。
     * 消息摘除与通知挂在 afterCommit（见类注释）。
     */
    @Transactional(rollbackFor = Exception.class)
    public void materialize(String member, HotTicketMessage message) {
        int qty = message.getQty() == null ? 1 : message.getQty();

        Order order = new Order();
        order.setOrderNo(message.getOrderNo());
        order.setGoodsId(message.getGoodsId());
        order.setBuyerId(message.getBuyerId());
        order.setSellerId(message.getSellerId());
        order.setDealType(1);                 // 热卖固定平台配送
        order.setGoodsType(1);
        order.setGoodsAmount(message.getGoodsAmount());
        order.setServiceFee(message.getServiceFee());
        order.setDeliveryFeePayer(message.getDeliveryFeePayer());
        order.setStatus(OrderStatus.PENDING_DELIVERY.getCode()); // 出生即待派单
        order.setQuantity(qty);
        order.setPickupLocation(message.getAddress()); // 收货地址快照
        order.setExpireTime(LocalDateTime.ofInstant(
                Instant.ofEpochMilli(message.getExpireAt() == null
                        ? System.currentTimeMillis() : message.getExpireAt()),
                ZoneId.systemDefault()));
        orderMapper.insert(order);

        // DB 账本无条件扣减 + 累销量：Redis 已在下单时卖掉这批货，条件失败只会把已确认票据永久卡死。
        // 预读不足只告警（Redis 是扣减权威、DB 是账本），宁可账面出现负数也要让票据走完
        Goods goods = goodsMapper.selectById(message.getGoodsId());
        if (goods == null) {
            throw new IllegalStateException("商品不存在，无法履约: goodsId=" + message.getGoodsId());
        }
        if (goods.getStock() != null && goods.getStock() < qty) {
            log.warn("Redis/DB stock mismatch: goodsId={}, dbStock={}, qty={}, orderNo={}",
                    message.getGoodsId(), goods.getStock(), qty, message.getOrderNo());
        }
        goodsMapper.update(null, new LambdaUpdateWrapper<Goods>()
                .eq(Goods::getId, message.getGoodsId())
                .setSql("stock = stock - " + qty + ", sales = sales + " + qty));

        // 配送工单：预检防重复（createDeliveryOrder 重复会抛异常并把外层事务打成 rollback-only）
        Long deliveryCount = deliveryOrderMapper.selectCount(new LambdaQueryWrapper<DeliveryOrder>()
                .eq(DeliveryOrder::getOrderId, order.getId()));
        if (deliveryCount == 0) {
            deliveryService.createDeliveryOrder(order.getId());
        }

        Long orderId = order.getId();
        Long buyerId = message.getBuyerId();
        String orderNo = message.getOrderNo();
        runAfterCommit(() -> {
            // ★LREM 必须在提交后★（Redis 不回滚，见类注释）
            stockService.finishProcessing(member);
            notifyUser(buyerId, orderId, orderNo,
                    "订单已确认（订单号: " + orderNo + "），已进入派单大厅等待跑腿接单");
        });

        log.info("热卖订单落库: orderId={}, orderNo={}, goodsId={}, qty={}",
                orderId, orderNo, message.getGoodsId(), qty);
    }

    // ── private ───────────────────────────────────────────────────────────────

    /** 取自身的 Spring 代理（CGLIB），经代理调用事务注解才会织入 */
    private HotOrderConsumer self() {
        return applicationContext.getBean(HotOrderConsumer.class);
    }

    private void notifyUser(Long userId, Long orderId, String orderNo, String msg) {
        try {
            notificationService.send(userId, "hot_order_confirmed", "订单状态更新", msg,
                    "{\"orderId\":" + orderId + ",\"orderNo\":\"" + orderNo + "\"}");
        } catch (Exception e) {
            log.warn("发送热卖订单通知失败: userId={}, orderNo={}", userId, orderNo, e);
        }
    }

    /** 事务提交后执行（无事务上下文时立即执行）：LREM/通知等旁路，回滚后不产生副作用 */
    private void runAfterCommit(Runnable task) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    try {
                        task.run();
                    } catch (Exception e) {
                        log.warn("热卖落库事务提交后回调执行失败", e);
                    }
                }
            });
        } else {
            try {
                task.run();
            } catch (Exception e) {
                log.warn("热卖落库旁路任务执行失败", e);
            }
        }
    }
}
