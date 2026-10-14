package com.campus.trade.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.common.entity.Address;
import com.campus.common.entity.BanRecord;
import com.campus.common.entity.User;
import com.campus.common.exception.BusinessException;
import com.campus.common.mapper.AddressMapper;
import com.campus.common.mapper.UserMapper;
import com.campus.common.service.BanService;
import com.campus.common.service.NotificationService;
import com.campus.trade.dto.CreateHotOrderReq;
import com.campus.trade.dto.HotTicketMessage;
import com.campus.trade.dto.HotTicketVO;
import com.campus.trade.entity.Goods;
import com.campus.trade.entity.Order;
import com.campus.trade.enums.OrderStatus;
import com.campus.trade.mapper.GoodsMapper;
import com.campus.trade.mapper.OrderMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;

/**
 * 热卖商品订单服务：管理员发布的多库存商品，走「下单占票据 → 二次确认 → worker 落库进派单大厅」。
 *
 * <p><b>订单的「产生」= worker 消费 list 的时刻</b>：下单/确认阶段都只动 Redis，
 * MySQL 订单行由 {@link HotOrderConsumer} 消费已确认消息时插入。抢购瞬间订单只以消息形态存在。
 *
 * <p>并发与一致性（关键约定，改动前先读）：
 * <ul>
 *   <li><b>下单</b>：Lua 原子执行「查库存+DECRBY+RPUSH 消息进待确认桶」，无 DB 写、无补偿。</li>
 *   <li><b>确认 vs 超时/取消</b>：两边对同一成员串 LREM，恰好一方拿到 1（原子仲裁），
 *       确认方把消息迁入 {@code hot:confirmed:queue}，释放方 INCRBY 回补库存。</li>
 *   <li><b>消费落库</b>：worker 认领（LPOP→processing）→ @Transactional 落库 → <b>afterCommit 才
 *       LREM processing</b>（Redis 不随 MySQL 回滚，事务内摘除+回滚=票据丢失）。</li>
 *   <li><b>超时释放</b>：pending 桶扫描比对消息内 {@code expireAt}（RedisDelayQueue 需要 Long bizId，
 *       票据无 DB id，故走扫描）。精度 = 扫描间隔。</li>
 * </ul>
 *
 * <p>业务规则（已定稿）：货款线下结算（与现有二手一致），二次确认只是购买承诺；
 * 确认截止默认 60s（{@code trade.hot-order.confirm-timeout}）；限购数量可选；
 * 确认后不允许自助取消（货已进派单流程），走人工/纠纷处理。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HotSaleOrderService {

    private final OrderMapper orderMapper;
    private final GoodsMapper goodsMapper;
    private final AddressMapper addressMapper;
    private final UserMapper userMapper;
    private final HotSaleStockService stockService;
    private final HotTicketService ticketService;
    private final RedissonClient redisson;
    private final BanService banService;
    private final NotificationService notificationService;

    /** 平台配送服务费（元），与普通配送订单同一配置 */
    @Value("${delivery.service-fee:5.00}")
    private BigDecimal defaultServiceFee;

    /** 二次确认截止时长，默认 60s */
    @Value("${trade.hot-order.confirm-timeout:60s}")
    private Duration confirmTimeout;

    // ── 下单：Lua 扣库存 + 消息入待确认桶（无 DB 写） ─────────────────────────

    /**
     * 热卖下单：校验通过后 Lua 扣减库存并把订单消息 RPUSH 进待确认桶。
     * 此刻不存在 MySQL 订单行——票据即订单，等买家二次确认后由 worker 落库。
     */
    public HotTicketVO placeOrder(Long buyerId, CreateHotOrderReq req) {
        // 交易封禁校验（与普通下单同口径）
        BanRecord buyerBan = banService.checkUserBan(buyerId, "trade");
        if (buyerBan != null) {
            throw new BusinessException("您的交易功能已被封禁，原因：" + buyerBan.getReason()
                    + "，解封时间：" + buyerBan.getBanUntil());
        }

        int quantity = req.getQuantity() == null ? 1 : req.getQuantity();

        // 下单幂等：短窗口内同一幂等键只允许提交一次（防重复点击）
        String idemKey = "hot:order:idem:" + buyerId + ":"
                + (StringUtils.hasText(req.getIdempotentKey()) ? req.getIdempotentKey() : req.getGoodsId());
        RBucket<String> idemBucket = redisson.getBucket(idemKey);
        if (!idemBucket.setIfAbsent("1", Duration.ofSeconds(30))) {
            throw new BusinessException("请勿重复提交订单，请稍后再试");
        }
        boolean created = false;

        try {
            Goods goods = goodsMapper.selectById(req.getGoodsId());
            if (goods == null) {
                throw new BusinessException("商品不存在");
            }
            if (goods.getGoodsType() == null || goods.getGoodsType() != 1) {
                throw new BusinessException("该商品不是热卖商品");
            }
            if (goods.getStatus() != 0) {
                throw new BusinessException("商品已下架或已售罄");
            }

            // 同一买家同一商品只允许一笔未确认票据（仅待确认阶段；确认后可再买，限购仍生效）。
            // 权威检查在 deduct Lua 内（与 RPUSH 原子），此处预扫只为精确报错
            for (String member : stockService.listPending(goods.getId())) {
                HotTicketMessage existing = stockService.parseMember(member);
                if (existing != null && buyerId.equals(existing.getBuyerId())) {
                    throw new BusinessException("您有一笔未确认的订单，请先确认或等待超时释放");
                }
            }

            // 限购：DB 非取消订单 + Redis 在途票据（按 orderNo 去重，避免 processing 中
            // 已落库消息与 DB 行双计）
            if (goods.getPurchaseLimit() != null && goods.getPurchaseLimit() > 0) {
                List<Order> countedOrders = orderMapper.selectList(new LambdaQueryWrapper<Order>()
                        .eq(Order::getBuyerId, buyerId)
                        .eq(Order::getGoodsId, goods.getId())
                        .ne(Order::getStatus, OrderStatus.CANCELLED.getCode()));
                int bought = countedOrders.stream()
                        .mapToInt(o -> o.getQuantity() == null ? 1 : o.getQuantity())
                        .sum();
                for (HotTicketMessage msg : ticketService.listAllPending()) {
                    if (isInFlightTicket(msg, buyerId, goods.getId()) && !existsInDb(msg.getOrderNo())) {
                        bought += msg.getQty() == null ? 1 : msg.getQty();
                    }
                }
                for (HotTicketMessage msg : ticketService.listConfirmed()) {
                    if (isInFlightTicket(msg, buyerId, goods.getId()) && !existsInDb(msg.getOrderNo())) {
                        bought += msg.getQty() == null ? 1 : msg.getQty();
                    }
                }
                for (HotTicketMessage msg : ticketService.listProcessing()) {
                    if (isInFlightTicket(msg, buyerId, goods.getId()) && !existsInDb(msg.getOrderNo())) {
                        bought += msg.getQty() == null ? 1 : msg.getQty();
                    }
                }
                if (bought + quantity > goods.getPurchaseLimit()) {
                    throw new BusinessException("超出限购数量（每人限购 " + goods.getPurchaseLimit() + " 件）");
                }
            }

            // 收货地址快照：指定地址或默认地址
            Address buyerAddress = resolveBuyerAddress(buyerId, req.getAddressId());

            // Redis key 缺失（Redis 重启等）按 DB 值补建，避免永久拒单；不覆盖已有计数
            if (stockService.getAvailable(goods.getId()) == null && goods.getStock() != null) {
                stockService.ensureStock(goods.getId(), goods.getStock());
            }

            String orderNo = generateOrderNo();
            long expireAt = System.currentTimeMillis() + confirmTimeout.toMillis();

            HotTicketMessage message = new HotTicketMessage();
            message.setOrderNo(orderNo);
            message.setBuyerId(buyerId);
            try {
                User buyer = userMapper.selectById(buyerId);
                message.setBuyerNickname(buyer != null ? buyer.getNickname() : null);
            } catch (Exception ignored) {
            }
            message.setGoodsId(goods.getId());
            message.setGoodsTitle(goods.getTitle());
            message.setGoodsImage(firstImage(goods.getImages()));
            message.setSellerId(goods.getUserId()); // 发布管理员
            message.setQty(quantity);
            message.setGoodsAmount(goods.getPrice().multiply(BigDecimal.valueOf(quantity)));
            message.setServiceFee(defaultServiceFee);
            message.setDeliveryFeePayer(StringUtils.hasText(req.getDeliveryFeePayer())
                    ? req.getDeliveryFeePayer() : "buyer");
            message.setAddress(buyerAddress.getBuilding() + " " + buyerAddress.getDetail()
                    + " (" + buyerAddress.getContactName() + " " + buyerAddress.getPhone() + ")");
            message.setCreatedAt(System.currentTimeMillis());
            message.setExpireAt(expireAt);

            String member = stockService.toMember(message);

            // Lua 扣减：查库存+DECRBY+RPUSH 消息 原子执行
            long deducted = stockService.deduct(goods.getId(), member, quantity, buyerId);
            if (deducted == -1) {
                throw new BusinessException("商品库存未就绪，请稍后重试");
            }
            if (deducted == 0) {
                throw new BusinessException("商品已售罄");
            }
            if (deducted == -2) {
                throw new BusinessException("您有一笔未确认的订单，请先确认或等待超时释放");
            }

            notifyUser(buyerId, "hot_order_created", orderNo,
                    "热卖订单已创建（订单号: " + orderNo + "），请在 " + confirmTimeout.getSeconds()
                            + " 秒内确认，超时将自动取消并释放库存");

            log.info("热卖票据创建: orderNo={}, goodsId={}, quantity={}", orderNo, goods.getId(), quantity);
            created = true;
            return ticketService.toVO(message, HotTicketVO.PHASE_PENDING_CONFIRM);
        } finally {
            if (!created) {
                try {
                    idemBucket.delete();
                } catch (Exception e) {
                    log.warn("释放热卖下单幂等键失败: {}", idemKey, e);
                }
            }
        }
    }

    // ── 二次确认：消息迁入已确认队列（仍无 DB 写） ────────────────────────────

    /**
     * 买家二次确认（购买承诺）：消息从待确认桶迁入已确认队列，返回「处理中」票据。
     * 落库/建配送工单/sales 累加全部移交 {@link HotOrderConsumer}。
     */
    public HotTicketVO confirmTicket(Long buyerId, String orderNo, Long goodsId) {
        HotTicketMessage message = ticketService.findPendingMessage(orderNo, goodsId);
        if (message == null) {
            // 可能已确认/已超时/已落库，给前端可刷新的终态语义
            throw new BusinessException("票据已确认或已超时释放，请刷新后查看");
        }
        if (!message.getBuyerId().equals(buyerId)) {
            throw new BusinessException(403, "无权操作");
        }

        boolean moved = stockService.confirm(message.getGoodsId(), orderNo);
        if (!moved) {
            throw new BusinessException("票据已确认或已超时释放，请刷新后查看");
        }

        log.info("热卖票据确认入队: orderNo={}, goodsId={}", orderNo, message.getGoodsId());
        return ticketService.toVO(message, HotTicketVO.PHASE_PROCESSING);
    }

    // ── 释放：超时 / 待确认期取消（同一幂等路径） ─────────────────────────────

    /**
     * 买家在待确认期主动取消：摘消息 + 回补库存。
     * 确认后不允许自助取消（货已进派单流程），走人工/纠纷。
     */
    public HotTicketVO cancelTicket(Long buyerId, String orderNo, Long goodsId) {
        HotTicketMessage message = ticketService.findPendingMessage(orderNo, goodsId);
        if (message == null) {
            throw new BusinessException("票据已确认并进入派单流程，如需取消请联系客服或发起纠纷");
        }
        if (!message.getBuyerId().equals(buyerId)) {
            throw new BusinessException(403, "无权操作");
        }

        boolean released = stockService.release(message.getGoodsId(), orderNo);
        if (!released) {
            throw new BusinessException("票据已确认或已释放，请刷新后查看");
        }

        notifyUser(buyerId, "hot_order_released", orderNo,
                "订单已取消（订单号: " + orderNo + "），库存已释放");
        log.info("热卖票据取消释放: orderNo={}, goodsId={}, qty={}", orderNo, message.getGoodsId(), message.getQty());

        HotTicketVO vo = ticketService.toVO(message, HotTicketVO.PHASE_PENDING_CONFIRM);
        vo.setPhase(HotTicketVO.PHASE_CANCELLED);
        vo.setPhaseDesc("已取消");
        vo.setStatus(OrderStatus.CANCELLED.getCode());
        vo.setRemainSeconds(0L);
        return vo;
    }

    /**
     * 超时释放（pending 桶扫描发现 expireAt 已过时调用）：幂等，重复执行无副作用。
     * 已确认票据不在 pending 桶，不会被误释放。
     */
    public void releaseTicket(HotTicketMessage message, String reason) {
        boolean released = stockService.release(message.getGoodsId(), message.getOrderNo());
        if (!released) {
            // 已被确认/已释放/已取消，无副作用
            return;
        }
        String msg = "timeout".equals(reason)
                ? "订单超时未确认已自动取消（订单号: " + message.getOrderNo() + "），库存已释放"
                : "订单已取消（订单号: " + message.getOrderNo() + "），库存已释放";
        notifyUser(message.getBuyerId(), "hot_order_released", message.getOrderNo(), msg);
        log.info("热卖票据释放库存: orderNo={}, goodsId={}, qty={}, reason={}",
                message.getOrderNo(), message.getGoodsId(), message.getQty(), reason);
    }

    // ── 对账：处理中转区残留清理 ──────────────────────────────────────────────

    /**
     * 处理中转区对账（定时任务调用，幂等）。新模型里「有消息无 DB 行」是常态而非孤儿，
     * 这里只处理 worker 崩溃留下的残留：
     * <ul>
     *   <li>DB 已有 orderNo（落库成功但 afterCommit LREM 未执行）→ 仅摘消息；</li>
     *   <li>DB 无 orderNo（认领后落库前崩溃）→ 摘出后重投已确认队列，交给 worker 重试。
     *       不释放：买家已确认，是瞬时故障而非超时。</li>
     * </ul>
     */
    public void reconcileProcessingTickets() {
        for (String member : stockService.listProcessing()) {
            try {
                HotTicketMessage message = stockService.parseMember(member);
                if (message == null) {
                    stockService.removeProcessing(member);
                    log.warn("清理非法热卖处理中消息: {}", member);
                    continue;
                }
                if (existsInDb(message.getOrderNo())) {
                    stockService.removeProcessing(member);
                    log.info("清理热卖处理中残留（订单已落库）: orderNo={}", message.getOrderNo());
                } else {
                    stockService.removeProcessing(member);
                    stockService.pushConfirmed(member);
                    log.warn("热卖处理中消息未落库，已重投已确认队列: orderNo={}", message.getOrderNo());
                }
            } catch (Exception e) {
                log.warn("热卖处理中消息对账失败: {}", member, e);
            }
        }
    }

    // ── private helpers ───────────────────────────────────────────────────────

    private boolean isInFlightTicket(HotTicketMessage msg, Long buyerId, Long goodsId) {
        return msg != null && buyerId.equals(msg.getBuyerId())
                && goodsId.equals(msg.getGoodsId()) && msg.getQty() != null;
    }

    private boolean existsInDb(String orderNo) {
        Long count = orderMapper.selectCount(new LambdaQueryWrapper<Order>()
                .eq(Order::getOrderNo, orderNo));
        return count != null && count > 0;
    }

    private Address resolveBuyerAddress(Long buyerId, Long addressId) {
        Address address;
        if (addressId != null) {
            address = addressMapper.selectOne(new LambdaQueryWrapper<Address>()
                    .eq(Address::getId, addressId)
                    .eq(Address::getUserId, buyerId));
            if (address == null) {
                throw new BusinessException("收货地址不存在");
            }
        } else {
            // 未指定地址：先取默认地址，无默认则取最近一条；完全没有地址时明确拒绝下单
            address = addressMapper.selectOne(new LambdaQueryWrapper<Address>()
                    .eq(Address::getUserId, buyerId)
                    .eq(Address::getIsDefault, 1)
                    .last("LIMIT 1"));
            if (address == null) {
                address = addressMapper.selectOne(new LambdaQueryWrapper<Address>()
                        .eq(Address::getUserId, buyerId)
                        .orderByDesc(Address::getUpdateTime)
                        .last("LIMIT 1"));
            }
            if (address == null) {
                throw new BusinessException("买家尚未填写收货地址，请先在收货地址中添加");
            }
        }
        return address;
    }

    private String firstImage(String imagesJson) {
        if (imagesJson == null || imagesJson.isBlank()) {
            return null;
        }
        try {
            List<String> images = new com.fasterxml.jackson.databind.ObjectMapper()
                    .readValue(imagesJson, List.class);
            return images.isEmpty() ? null : images.get(0);
        } catch (Exception e) {
            return null;
        }
    }

    private void notifyUser(Long userId, String type, String orderNo, String msg) {
        try {
            notificationService.send(userId, type, "订单状态更新", msg,
                    "{\"orderNo\":\"" + orderNo + "\"}");
        } catch (Exception e) {
            log.warn("发送热卖订单通知失败: userId={}, type={}", userId, type, e);
        }
    }

    /** 事务提交后执行（无事务上下文时立即执行）：通知/投递任务等旁路操作，回滚后不产生副作用 */
    static void runAfterCommit(Runnable task) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    try {
                        task.run();
                    } catch (Exception e) {
                        log.warn("事务提交后回调执行失败", e);
                    }
                }
            });
        } else {
            try {
                task.run();
            } catch (Exception e) {
                log.warn("旁路任务执行失败", e);
            }
        }
    }

    /** 订单号：时间戳(13位) + 8位随机字符，与普通订单同格式 */
    public static String generateOrderNo() {
        return "ORD" + System.currentTimeMillis()
                + cn.hutool.core.util.RandomUtil.randomString(8).toUpperCase();
    }
}
