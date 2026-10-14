package com.campus.trade.service;

import com.campus.trade.dto.HotTicketMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scripting.support.ResourceScriptSource;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 热卖商品 Redis 队列服务：Lua 脚本保证"查库存+扣减+订单消息入队"的原子性，
 * 扛住热卖抢购的并发扣减；DB 侧库存只作账本/展示，运行时扣减权威在 {@code hot:stock:{goodsId}}。
 *
 * <p>Key 设计（消息即订单，消费才落库）：
 * <ul>
 *   <li>{@code hot:stock:{goodsId}}     可售库存计数（扣减权威）</li>
 *   <li>{@code hot:pending:{goodsId}}   待确认桶（per goods），成员 = 订单消息 JSON</li>
 *   <li>{@code hot:confirmed:queue}     已确认队列（全局），等 worker 落库</li>
 *   <li>{@code hot:order:processing}    消费中转区（已认领、尚未摘除的可靠消费缓冲）</li>
 * </ul>
 *
 * <p>list 成员资格即状态仲裁：确认迁移与超时释放都对同一成员串 LREM，LREM 只会成功一次，
 * 两方恰好一方拿到 1——这就是"确认进派单"与"超时释放库存"之间的原子互斥，天然幂等。
 *
 * <p>降级策略：stock key 缺失（Redis 重启/未初始化）时 Lua 返回 -1 fail-close 拒单，
 * 宁可少卖不可超卖；由 {@link #ensureStock} 在下单前按 DB 值补建。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HotSaleStockService {

    private static final String STOCK_KEY_PREFIX = "hot:stock:";
    private static final String PENDING_KEY_PREFIX = "hot:pending:";
    private static final String CONFIRMED_KEY = "hot:confirmed:queue";
    private static final String PROCESSING_KEY = "hot:order:processing";

    /** 消息 JSON 编解码（成员串格式的唯一定义处） */
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final StringRedisTemplate redis;

    /** 下单扣库存：查库存+DECRBY+RPUSH 消息 原子执行 */
    private final DefaultRedisScript<Long> deductScript = new DefaultRedisScript<>();
    /** 确认迁移：LREM pending + RPUSH confirmed，与超时释放互斥 */
    private final DefaultRedisScript<Long> confirmScript = new DefaultRedisScript<>();
    /** 释放回补：LREM+INCRBY 原子执行，消息不在则不回补（幂等） */
    private final DefaultRedisScript<Long> releaseScript = new DefaultRedisScript<>();
    /** 消费者认领：LPOP confirmed + RPUSH processing 原子执行 */
    private final DefaultRedisScript<String> claimScript = new DefaultRedisScript<>();

    @PostConstruct
    void initScripts() {
        loadLongScript(deductScript, "lua/hot_stock_deduct.lua");
        loadLongScript(confirmScript, "lua/hot_order_confirm.lua");
        loadLongScript(releaseScript, "lua/hot_stock_release.lua");
        claimScript.setScriptSource(new ResourceScriptSource(new ClassPathResource("lua/hot_ticket_claim.lua")));
        claimScript.setResultType(String.class);
        log.info("热卖库存/票据 Lua 脚本已加载");
    }

    // ── 消息序列化 ─────────────────────────────────────────────────────────────

    /** 消息对象 → list 成员串（JSON） */
    public String toMember(HotTicketMessage message) {
        try {
            return MAPPER.writeValueAsString(message);
        } catch (Exception e) {
            throw new IllegalStateException("热卖票据消息序列化失败: " + message.getOrderNo(), e);
        }
    }

    /** list 成员串 → 消息对象；非法成员返回 null 由调用方跳过 */
    public HotTicketMessage parseMember(String member) {
        try {
            return MAPPER.readValue(member, HotTicketMessage.class);
        } catch (Exception e) {
            log.warn("热卖票据消息解析失败（非法成员）: {}", member, e);
            return null;
        }
    }

    // ── 下单 / 确认 / 释放 / 消费 ─────────────────────────────────────────────

    /**
     * 下单扣库存并把消息 RPUSH 进待确认桶（原子）。
     *
     * @return 1=成功 0=库存不足 -1=库存未初始化（fail-close 拒单） -2=该商品已有在途票
     */
    public long deduct(Long goodsId, String messageJson, int quantity, Long buyerId) {
        Long result = redis.execute(deductScript,
                List.of(stockKey(goodsId), pendingKey(goodsId)),
                messageJson, String.valueOf(quantity),
                String.valueOf(buyerId), String.valueOf(goodsId));
        return result == null ? -1 : result;
    }

    /**
     * 二次确认：消息从待确认桶迁入已确认队列（与超时释放对同一成员 LREM 互斥）。
     *
     * @return true=迁移成功；false=消息已被超时释放/已取消摘走
     */
    public boolean confirm(Long goodsId, String orderNo) {
        Long result = redis.execute(confirmScript,
                List.of(pendingKey(goodsId), CONFIRMED_KEY),
                orderNo);
        return result != null && result == 1;
    }

    /**
     * 释放库存回补（超时未确认/待确认期取消），qty 取自消息本身。
     *
     * @return true=确已回补；false=消息不存在（已确认或已释放过，无副作用）
     */
    public boolean release(Long goodsId, String orderNo) {
        Long result = redis.execute(releaseScript,
                List.of(stockKey(goodsId), pendingKey(goodsId)),
                orderNo);
        return result != null && result == 1;
    }

    /**
     * 消费者认领一条已确认消息（LPOP→RPUSH processing，原子）。
     *
     * @return 消息 JSON；队列已空返回 null
     */
    public String claimConfirmed() {
        return redis.execute(claimScript, List.of(CONFIRMED_KEY), List.of());
    }

    /**
     * 落库提交后从处理中转区摘除消息（幂等，成员不在时返回 0）。
     *
     * <p><b>必须在事务提交后调用</b>——Redis 不随 MySQL 回滚，事务内摘除+回滚=票据丢失。
     */
    public long finishProcessing(String messageJson) {
        Long removed = redis.opsForList().remove(PROCESSING_KEY, 1, messageJson);
        return removed == null ? 0 : removed;
    }

    /** 对账用：重投已确认队列（处理中转区崩溃残留、无 DB 行者回队重试） */
    public void pushConfirmed(String messageJson) {
        redis.opsForList().rightPush(CONFIRMED_KEY, messageJson);
    }

    /** 对账用：从处理中转区移除消息（幂等） */
    public void removeProcessing(String messageJson) {
        redis.opsForList().remove(PROCESSING_KEY, 1, messageJson);
    }

    // ── 查询（票据展示/对账/管理端） ──────────────────────────────────────────

    /** 列出某商品的待确认消息（成员 JSON 列表） */
    public List<String> listPending(Long goodsId) {
        List<String> members = redis.opsForList().range(pendingKey(goodsId), 0, -1);
        return members == null ? List.of() : members;
    }

    /** 列出已确认队列（等 worker 落库） */
    public List<String> listConfirmed() {
        List<String> members = redis.opsForList().range(CONFIRMED_KEY, 0, -1);
        return members == null ? List.of() : members;
    }

    /** 列出处理中转区消息 */
    public List<String> listProcessing() {
        List<String> members = redis.opsForList().range(PROCESSING_KEY, 0, -1);
        return members == null ? List.of() : members;
    }

    /** 已确认队列长度（积压观测） */
    public long confirmedQueueLength() {
        Long len = redis.opsForList().size(CONFIRMED_KEY);
        return len == null ? 0 : len;
    }

    /** 处理中转区长度（积压观测） */
    public long processingLength() {
        Long len = redis.opsForList().size(PROCESSING_KEY);
        return len == null ? 0 : len;
    }

    /** 待确认桶长度（在途未确认票据数） */
    public long pendingLength(Long goodsId) {
        Long len = redis.opsForList().size(pendingKey(goodsId));
        return len == null ? 0 : len;
    }

    // ── 库存计数 ──────────────────────────────────────────────────────────────

    /**
     * 发布/补货时同步库存计数：补货用 INCRBY 而非 SET，
     * 避免把在途扣减（已下单未确认）冲掉造成超卖。
     */
    public void adjustStock(Long goodsId, int delta) {
        redis.opsForValue().increment(stockKey(goodsId), delta);
        log.info("热卖库存已调整: goodsId={}, delta={}", goodsId, delta);
    }

    /** 发布初始化：新建库存计数（仅发布时调用，key 应当是全新的） */
    public void initStock(Long goodsId, int stock) {
        redis.opsForValue().set(stockKey(goodsId), String.valueOf(stock));
        log.info("热卖库存已初始化: goodsId={}, stock={}", goodsId, stock);
    }

    /**
     * 下单前兜底补建：stock key 缺失（Redis 重启/数据丢失）时按 DB 可售库存重建，
     * 避免永久拒单。只补建缺失 key，绝不覆盖已有计数（在途扣减以 Redis 为准）。
     */
    public void ensureStock(Long goodsId, int dbStock) {
        redis.opsForValue().setIfAbsent(stockKey(goodsId), String.valueOf(dbStock));
    }

    /** 读取可售库存（列表/详情展示用），key 缺失返回 null 由调用方回退 DB 值 */
    public Long getAvailable(Long goodsId) {
        String val = redis.opsForValue().get(stockKey(goodsId));
        if (val == null) {
            return null;
        }
        try {
            return Long.parseLong(val);
        } catch (NumberFormatException e) {
            log.warn("热卖库存计数非法: goodsId={}, val={}", goodsId, val);
            return null;
        }
    }

    // ── private ───────────────────────────────────────────────────────────────

    private static String stockKey(Long goodsId) {
        return STOCK_KEY_PREFIX + goodsId;
    }

    private static String pendingKey(Long goodsId) {
        return PENDING_KEY_PREFIX + goodsId;
    }

    private static void loadLongScript(DefaultRedisScript<Long> script, String path) {
        script.setScriptSource(new ResourceScriptSource(new ClassPathResource(path)));
        script.setResultType(Long.class);
    }
}
