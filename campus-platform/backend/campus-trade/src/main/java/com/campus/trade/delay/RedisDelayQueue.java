package com.campus.trade.delay;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 基于 Redis zset 的延时队列。
 *
 * <p>实现：zset 的 member 为任务标识（{@code type|bizId}，稳定身份便于取消），
 * score 为到期执行时间戳（毫秒）。轮询线程每隔 {@code trade.delay-queue.poll-interval-ms}
 * 取出到期任务并派发给注册的处理器。
 *
 * <p>投递语义：
 * <ul>
 *   <li>同一 {@code type|bizId} 重复投递会覆盖 score（即重新定时），不会产生重复任务。</li>
 *   <li>消费侧用 ZREM 原子认领（返回 1 才处理），多实例部署下同一任务只会被一个实例消费。</li>
 *   <li>处理失败按次数指数退避重投（1 分钟、2 分钟），超过最大尝试次数进入死信 zset 留痕。</li>
 * </ul>
 *
 * <p>如果没有这个文件：订单超时取消/自动确认收货只能靠每日 cron 批量扫描，
 * 超时处理最长延迟近一天，无法做到按单精确到期。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisDelayQueue {

    /** 延时任务 zset：member = type|bizId，score = 到期时间戳（毫秒） */
    private static final String QUEUE_KEY = "delay:queue:orders";
    /** 重试次数记录：hash，field = type|bizId，value = 已失败次数 */
    private static final String ATTEMPTS_KEY = "delay:queue:orders:attempts";
    /** 死信 zset：超过最大重试次数的任务，留痕排查 */
    private static final String DEAD_KEY = "delay:queue:orders:dead";
    /** 单个任务最大尝试次数（含首次） */
    private static final int MAX_ATTEMPTS = 3;
    /** 单次轮询最多取出的任务数 */
    private static final int BATCH_SIZE = 100;

    private final StringRedisTemplate redis;

    /** 任务类型 → 处理器 */
    private final Map<String, DelayTaskHandler> handlers = new ConcurrentHashMap<>();

    /** 延时任务处理器：bizId 为业务 ID（如订单 ID）。抛出异常表示处理失败，需要重试。 */
    @FunctionalInterface
    public interface DelayTaskHandler {
        void handle(Long bizId) throws Exception;
    }

    @PostConstruct
    void init() {
        log.info("Redis 延时队列已启动: queue={}", QUEUE_KEY);
    }

    /** 注册任务处理器。 */
    public void registerHandler(String type, DelayTaskHandler handler) {
        handlers.put(type, handler);
    }

    /**
     * 投递延时任务。
     *
     * @param type            任务类型（需先 {@link #registerHandler} 注册处理器）
     * @param bizId           业务 ID
     * @param executeAtMillis 到期执行时间戳（毫秒）
     */
    public void schedule(String type, Long bizId, long executeAtMillis) {
        redis.opsForZSet().add(QUEUE_KEY, member(type, bizId), executeAtMillis);
    }

    /**
     * 取消延时任务（订单进入终态或状态流转出可处理范围时调用）。
     *
     * @return 是否确有任务被取消
     */
    public boolean cancel(String type, Long bizId) {
        String m = member(type, bizId);
        Long removed = redis.opsForZSet().remove(QUEUE_KEY, m);
        redis.opsForHash().delete(ATTEMPTS_KEY, m);
        return removed != null && removed > 0;
    }

    /** 轮询取出到期任务并派发。fixedDelay 保证上一轮结束后再计时，不会堆积。 */
    @Scheduled(fixedDelayString = "${trade.delay-queue.poll-interval-ms:5000}")
    public void poll() {
        Set<String> due = redis.opsForZSet()
                .rangeByScore(QUEUE_KEY, 0, System.currentTimeMillis(), 0, BATCH_SIZE);
        if (due == null || due.isEmpty()) {
            return;
        }
        for (String m : due) {
            // ZREM 原子认领：返回 1 的执行者才处理，多实例部署下不会重复消费
            Long removed = redis.opsForZSet().remove(QUEUE_KEY, m);
            if (removed == null || removed == 0) {
                continue;
            }
            dispatch(m);
        }
    }

    private void dispatch(String m) {
        int sep = m.indexOf('|');
        if (sep <= 0 || sep == m.length() - 1) {
            log.error("延时任务 member 格式非法，丢弃: {}", m);
            return;
        }
        String type = m.substring(0, sep);
        Long bizId;
        try {
            bizId = Long.parseLong(m.substring(sep + 1));
        } catch (NumberFormatException e) {
            log.error("延时任务 bizId 非法，丢弃: {}", m);
            return;
        }

        DelayTaskHandler handler = handlers.get(type);
        if (handler == null) {
            log.warn("延时任务未注册处理器，丢弃: {}", m);
            return;
        }

        try {
            handler.handle(bizId);
            redis.opsForHash().delete(ATTEMPTS_KEY, m);
        } catch (Exception e) {
            long attempts = incrementAttempts(m);
            if (attempts < MAX_ATTEMPTS) {
                // 指数退避重投：1 分钟、2 分钟
                long backoffMs = 60_000L * attempts;
                redis.opsForZSet().add(QUEUE_KEY, m, System.currentTimeMillis() + backoffMs);
                log.warn("延时任务处理失败（第{}次），{}ms 后重试: {}", attempts, backoffMs, m, e);
            } else {
                redis.opsForZSet().add(DEAD_KEY, m, System.currentTimeMillis());
                redis.opsForHash().delete(ATTEMPTS_KEY, m);
                log.error("延时任务超过最大重试次数，进入死信队列: {}", m, e);
            }
        }
    }

    private long incrementAttempts(String m) {
        Object val = redis.opsForHash().get(ATTEMPTS_KEY, m);
        long attempts = 1;
        if (val != null) {
            try {
                attempts = Long.parseLong(val.toString()) + 1;
            } catch (NumberFormatException ignored) {
            }
        }
        redis.opsForHash().put(ATTEMPTS_KEY, m, String.valueOf(attempts));
        return attempts;
    }

    /** 队列中尚未到期的任务数（监控用） */
    public long pendingCount() {
        Long size = redis.opsForZSet().zCard(QUEUE_KEY);
        return size == null ? 0 : size;
    }

    private static String member(String type, Long bizId) {
        return type + "|" + bizId;
    }
}
