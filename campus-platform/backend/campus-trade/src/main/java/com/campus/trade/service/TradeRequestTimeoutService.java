package com.campus.trade.service;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.campus.trade.delay.RedisDelayQueue;
import com.campus.trade.entity.ChatMessage;
import com.campus.trade.entity.ChatSession;
import com.campus.trade.mapper.ChatMessageMapper;
import com.campus.trade.mapper.ChatSessionMapper;
import com.campus.trade.websocket.ChatWebSocketHandler;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;

/**
 * 交易请求超时处理：聊天中"发起交易"发一条快捷操作消息（msgType=3，extra 带 action），
 * 并在落库前扣减库存占位（0→3，见 {@link GoodsReservationService}），不建单；
 * 对方确认才创建订单。请求通过 {@link RedisDelayQueue} 投递精确到期的过期任务，
 * 到期后标记请求过期、恢复库存并 WebSocket 通知双方，前端据此恢复"发起交易"入口。
 *
 * <p>投递/撤销时机（见 {@code ChatServiceImpl.sendMessage}）：
 * <ul>
 *   <li>发出 action=self_pickup/delivery 的请求消息时：先在锁内扣减库存占位，再按会话维度投递过期任务。
 *       同会话重复发起（占位仍在自己手上）直接拒绝，不重新计时。</li>
 *   <li>发出 action=rejected 的拒绝消息时：释放库存占位并撤销过期任务。</li>
 *   <li>发出 action=confirmed 的确认消息时：不撤销过期任务（安全网），由建单成功后撤销——
 *       防止"只发确认消息未建单"（假确认/建单失败未重试）把商品永久卡在已预订。</li>
 * </ul>
 *
 * <p>过期处理幂等：恢复库存带持有者谓词只释放自己那笔占位，重复投递/重试无副作用；
 * 若请求已被确认且订单已接管占位，恢复自然空转。extra 中写入 {@code expired=true}
 * 作为持久化过期标记，双方刷新后也视为过期。
 */
@Slf4j
@Service
public class TradeRequestTimeoutService {

    /** 延时任务类型：交易请求超时作废 */
    public static final String TYPE_TRADE_REQUEST_EXPIRE = "trade_request_expire";

    /** 交易请求有效期，默认 12 小时 */
    @Value("${trade.request.expire-after:12h}")
    private Duration expireAfter;

    private final RedisDelayQueue delayQueue;
    private final ChatMessageMapper messageMapper;
    private final ChatSessionMapper sessionMapper;
    private final ChatWebSocketHandler wsHandler;
    private final GoodsReservationService goodsReservationService;

    public TradeRequestTimeoutService(RedisDelayQueue delayQueue,
                                      ChatMessageMapper messageMapper,
                                      ChatSessionMapper sessionMapper,
                                      @Lazy ChatWebSocketHandler wsHandler,
                                      GoodsReservationService goodsReservationService) {
        this.delayQueue = delayQueue;
        this.messageMapper = messageMapper;
        this.sessionMapper = sessionMapper;
        this.wsHandler = wsHandler;
        this.goodsReservationService = goodsReservationService;
    }

    /** 启动时向延时队列注册过期处理器 */
    @PostConstruct
    void registerHandler() {
        delayQueue.registerHandler(TYPE_TRADE_REQUEST_EXPIRE, this::expireRequest);
        log.info("交易请求过期任务已注册: 有效期={}", expireAfter);
    }

    /**
     * 交易消息落库前调用：请求消息扣减库存占位并写入 expireAt、投递过期任务；
     * 拒绝消息释放占位并撤销任务；确认消息不做任务处理（建单成功后由下单侧撤销）。
     * 非交易消息原样返回。抛出异常（如库存扣减失败）会中断消息发送并回滚落库。
     */
    public void onMessageSending(ChatMessage msg) {
        if (msg.getMsgType() != 3 || msg.getExtra() == null || msg.getExtra().isBlank()) {
            return;
        }
        JSONObject extra;
        try {
            extra = JSONUtil.parseObj(msg.getExtra());
        } catch (Exception e) {
            return;
        }
        String action = extra.getStr("action");
        if ("self_pickup".equals(action) || "delivery".equals(action)) {
            // 先扣减库存占位（锁内原子操作，重复发起/被他人抢占会抛错中断发送），
            // 扣减成功才写 expireAt 并投递过期任务，避免"消息已发但没占位"的错位
            Long goodsId = extra.getLong("goodsId");
            if (goodsId == null) {
                log.warn("交易请求缺少 goodsId，跳过库存占位: sessionId={}", msg.getSessionId());
            } else {
                goodsReservationService.reserveForRequest(goodsId, msg.getSessionId());
            }
            long expireAt = System.currentTimeMillis() + expireAfter.toMillis();
            extra.set("expireAt", expireAt);
            msg.setExtra(extra.toString());
            delayQueue.schedule(TYPE_TRADE_REQUEST_EXPIRE, msg.getSessionId(), expireAt);
            log.info("投递交易请求过期任务: sessionId={}, 到期时间戳={}", msg.getSessionId(), expireAt);
        } else if ("rejected".equals(action)) {
            // 拒绝：释放库存占位（仅释放自己持有的，幂等）并撤销过期任务
            releaseLatestRequest(msg.getSessionId());
            delayQueue.cancel(TYPE_TRADE_REQUEST_EXPIRE, msg.getSessionId());
            log.info("拒绝交易请求，已释放占位并撤销过期任务: sessionId={}", msg.getSessionId());
        } else if ("confirmed".equals(action)) {
            // 确认：不撤销过期任务。正常流程由 createOrder 成功后调用 cancelExpireTask 撤销；
            // 保留任务作为安全网，防止只发确认消息未建单时商品永久卡在已预订。
            log.info("收到交易确认消息，保留过期任务作为建单安全网: sessionId={}", msg.getSessionId());
        }
    }

    /**
     * 建单成功后调用：撤销该会话的请求过期任务（占位已转移给订单，请求生命周期结束）。
     */
    public void cancelExpireTask(Long sessionId) {
        boolean cancelled = delayQueue.cancel(TYPE_TRADE_REQUEST_EXPIRE, sessionId);
        if (cancelled) {
            log.info("建单成功，撤销交易请求过期任务: sessionId={}", sessionId);
        }
    }

    /**
     * 过期处理：恢复该会话最新请求的库存占位；若请求仍是未处理状态则标记过期并通知双方。
     * 幂等可重试；占位已被订单接管时恢复为空转。
     */
    public void expireRequest(Long sessionId) {
        ChatMessage request = findLatestRequestMessage(sessionId);
        if (request == null) {
            return;
        }
        JSONObject extra;
        try {
            extra = JSONUtil.parseObj(request.getExtra());
        } catch (Exception e) {
            return;
        }

        // 恢复库存：带持有者谓词只释放本会话那笔占位。无论请求/确认/拒绝走到哪一步，
        // 此调用都安全——已释放或被订单接管（reserve_ref=orderId）时是无副作用空转；
        // 反之若"确认了但一直没建单"，这里就是兜底释放，商品不会永久卡在已预订。
        Long goodsId = extra.getLong("goodsId");
        if (goodsId != null) {
            goodsReservationService.releaseByRequest(goodsId, sessionId);
        }

        if (Boolean.TRUE.equals(extra.getBool("expired", false))) {
            return;
        }
        // 仅当最新交易消息仍是这笔未处理请求时才标记过期（已被确认/拒绝则跳过）
        ChatMessage latest = findLatestTradeMessage(sessionId);
        if (latest == null || !latest.getId().equals(request.getId())) {
            return;
        }
        String action = extra.getStr("action");
        if (!"self_pickup".equals(action) && !"delivery".equals(action)) {
            return;
        }

        extra.set("expired", true);
        messageMapper.update(null, new LambdaUpdateWrapper<ChatMessage>()
                .eq(ChatMessage::getId, request.getId())
                .set(ChatMessage::getExtra, extra.toString()));

        ChatSession session = sessionMapper.selectById(sessionId);
        if (session != null) {
            Map<String, Object> payload = Map.of(
                    "type", "trade_request_expired",
                    "data", Map.of("sessionId", sessionId, "messageId", request.getId())
            );
            wsHandler.pushToUser(session.getBuyerId(), payload);
            wsHandler.pushToUser(session.getSellerId(), payload);
        }
        log.info("交易请求已超时作废: sessionId={}, messageId={}", sessionId, request.getId());
    }

    /** 释放该会话最新请求消息的库存占位（拒绝时调用），无请求消息则跳过 */
    private void releaseLatestRequest(Long sessionId) {
        ChatMessage request = findLatestRequestMessage(sessionId);
        if (request == null) {
            return;
        }
        try {
            JSONObject extra = JSONUtil.parseObj(request.getExtra());
            Long goodsId = extra.getLong("goodsId");
            if (goodsId != null) {
                goodsReservationService.releaseByRequest(goodsId, sessionId);
            }
        } catch (Exception e) {
            log.warn("拒绝交易请求时释放占位失败: sessionId={}", sessionId, e);
        }
    }

    /** 取会话内最新的请求消息（action=self_pickup/delivery），无则返回 null */
    private ChatMessage findLatestRequestMessage(Long sessionId) {
        return messageMapper.selectList(new LambdaQueryWrapper<ChatMessage>()
                .eq(ChatMessage::getSessionId, sessionId)
                .eq(ChatMessage::getMsgType, 3)
                .orderByDesc(ChatMessage::getId)
                .last("LIMIT 10")).stream()
                .filter(m -> m.getExtra() != null && !m.getExtra().isBlank())
                .filter(m -> {
                    try {
                        String action = JSONUtil.parseObj(m.getExtra()).getStr("action");
                        return "self_pickup".equals(action) || "delivery".equals(action);
                    } catch (Exception e) {
                        return false;
                    }
                })
                .findFirst()
                .orElse(null);
    }

    /** 取会话内最新的交易消息（msgType=3 且 extra 带 action），无则返回 null */
    private ChatMessage findLatestTradeMessage(Long sessionId) {
        return messageMapper.selectList(new LambdaQueryWrapper<ChatMessage>()
                .eq(ChatMessage::getSessionId, sessionId)
                .eq(ChatMessage::getMsgType, 3)
                .orderByDesc(ChatMessage::getId)
                .last("LIMIT 10")).stream()
                .filter(m -> m.getExtra() != null && !m.getExtra().isBlank())
                .filter(m -> {
                    try {
                        return JSONUtil.parseObj(m.getExtra()).getStr("action") != null;
                    } catch (Exception e) {
                        return false;
                    }
                })
                .findFirst()
                .orElse(null);
    }
}
