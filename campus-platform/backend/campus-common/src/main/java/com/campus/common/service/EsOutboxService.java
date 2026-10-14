package com.campus.common.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.common.entity.EsSyncOutbox;
import com.campus.common.es.EsSyncHandler;
import com.campus.common.mapper.EsSyncOutboxMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * ES 同步事务性发件箱服务（Transactional Outbox）。
 *
 * <p><b>为什么不用"业务直接调 ES"</b>：直接调用存在崩溃窗口——DB 已提交、进程在调 ES 前挂掉，
 * 同步意图就永久丢失。发件箱把"要同步谁"写进 {@code es_sync_outbox} 表，
 * 与业务写<b>同一个 DB 事务</b>，要么都提交、要么都回滚，进程怎么挂都能被捞回来。
 *
 * <p><b>三条触发路径</b>，互为兜底：
 * <ol>
 *   <li>{@link #recordIndex}/{@link #recordDelete} —— 事务提交后踢一脚（afterCommit），
 *       正常路径秒级可见；</li>
 *   <li>{@code @Scheduled} 定时轮询 —— 兜底，覆盖"踢一脚时进程挂了/上一轮还在跑"；</li>
 *   <li>失败指数退避后由轮询重试 —— ES 抖动/宕机期间意图积压在表里，恢复后自动追平。</li>
 * </ol>
 *
 * <p><b>幂等</b>：发件箱只存 (bizType, bizId, op)，不存实体快照；重放时处理器重新读库拉齐，
 * 同一行重复做 N 次结果相同。同一实体的多次变更被 {@code uk_biz} 唯一键压缩成一行最新意图。
 *
 * <p>处理器按 {@link EsSyncHandler#bizType()} 注册，common 层不依赖任何业务模块。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EsOutboxService {

    private final EsSyncOutboxMapper outboxMapper;
    private final List<EsSyncHandler> handlerList;

    /** 业务类型 → 处理器，启动时组装一次 */
    private volatile Map<String, EsSyncHandler> handlers;

    /** 单实例轮询互斥：踢一脚与定时任务并发时只跑一轮，避免重复消费 */
    private final AtomicBoolean polling = new AtomicBoolean(false);

    /** 单轮最多处理多少条，避免 ES 长时间不可用后追平时单次任务耗时过长 */
    @Value("${campus.es.outbox.batch-size:200}")
    private int batchSize;

    /** 失败重试退避上限（秒） */
    @Value("${campus.es.outbox.max-backoff-seconds:300}")
    private long maxBackoffSeconds;

    /** 已同步记录保留天数，超期由清理任务删除 */
    @Value("${campus.es.outbox.retain-days:7}")
    private int retainDays;

    /** 启动时登记处理器，便于一眼看出各业务类型的同步链路是否挂上 */
    @PostConstruct
    public void init() {
        resolveHandlers();
        log.info("ES 同步发件箱就绪，已注册处理器: {}", handlers.keySet());
    }

    // ── 写入侧：业务代码调用，必须与业务写同事务 ─────────────────────────────

    /**
     * 记录"重建文档"意图。请在业务写库的<b>同一事务内</b>调用。
     *
     * @param bizType 业务类型，见 {@link EsSyncHandler#TYPE_GOODS} 等
     * @param bizId   业务主键 ID
     */
    public void recordIndex(String bizType, Long bizId) {
        record(bizType, bizId, EsSyncHandler.OP_INDEX);
    }

    /**
     * 记录"移除文档"意图。请在业务写库的<b>同一事务内</b>调用。
     *
     * @param bizType 业务类型
     * @param bizId   业务主键 ID
     */
    public void recordDelete(String bizType, Long bizId) {
        record(bizType, bizId, EsSyncHandler.OP_DELETE);
    }

    /**
     * 写入/压缩同步意图，并在事务提交后踢一脚立即同步。
     *
     * <p>upsert 语句参与当前事务：业务回滚时本行一并回滚，不会留下幽灵待办；
     * 业务提交时本行一定在库里，崩溃也丢不了。
     */
    private void record(String bizType, Long bizId, String op) {
        if (bizType == null || bizId == null) {
            return;
        }
        try {
            outboxMapper.upsert(bizType, bizId, op);
        } catch (Exception e) {
            // 写发件箱失败绝不能静默：这是"丢数据"的唯一入口，必须让调用方感知
            log.error("写入 ES 同步发件箱失败, bizType={}, bizId={}, op={}: {}",
                    bizType, bizId, op, e.getMessage());
            throw e instanceof RuntimeException re ? re : new RuntimeException(e);
        }
        // 事务提交后再踢：此前读库的处理器看不到未提交数据，踢早了会索引到旧状态
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    kick();
                }
            });
        } else {
            kick();
        }
    }

    // ── 消费侧：轮询 / 踢一脚 / 清理 ─────────────────────────────────────────

    /** 提交后立即触发一轮同步（降可见延迟）；已有轮询在跑则跳过，那一轮会捞到本条 */
    public void kick() {
        tryRun();
    }

    /** 兜底轮询：即使踢一脚丢失（进程挂/上一轮占着），这里也会补齐 */
    @Scheduled(fixedDelayString = "${campus.es.outbox.poll-interval-ms:3000}")
    public void scheduledPoll() {
        tryRun();
    }

    private void tryRun() {
        if (!polling.compareAndSet(false, true)) {
            return;
        }
        try {
            drain();
        } catch (Exception e) {
            log.warn("ES 同步发件箱轮询异常: {}", e.getMessage());
        } finally {
            polling.set(false);
        }
    }

    /** 取一批待同步记录逐条执行，单条失败只影响自身（退避后重试），不拖垮整批 */
    private void drain() {
        List<EsSyncOutbox> batch = outboxMapper.selectList(new LambdaQueryWrapper<EsSyncOutbox>()
                .eq(EsSyncOutbox::getStatus, 0)
                .le(EsSyncOutbox::getNextRetryAt, LocalDateTime.now())
                .orderByAsc(EsSyncOutbox::getId)
                .last("LIMIT " + Math.max(batchSize, 1)));
        if (batch.isEmpty()) {
            return;
        }
        Map<String, EsSyncHandler> map = resolveHandlers();
        for (EsSyncOutbox item : batch) {
            EsSyncHandler handler = map.get(item.getBizType());
            if (handler == null) {
                // 未知业务类型：标记完成避免死循环占住队列，同时告警便于发现漏注册的处理器
                log.warn("未注册的 ES 同步业务类型 [{}]，跳过 bizId={}", item.getBizType(), item.getBizId());
                markDone(item);
                continue;
            }
            try {
                if (EsSyncHandler.OP_DELETE.equals(item.getOp())) {
                    handler.syncDelete(item.getBizId());
                } else {
                    handler.syncIndex(item.getBizId());
                }
                markDone(item);
            } catch (Exception e) {
                markRetry(item, e);
            }
        }
    }

    private Map<String, EsSyncHandler> resolveHandlers() {
        Map<String, EsSyncHandler> map = handlers;
        if (map == null) {
            Map<String, EsSyncHandler> built = new HashMap<>();
            for (EsSyncHandler h : handlerList) {
                built.put(h.bizType(), h);
            }
            handlers = built;
            map = built;
        }
        return map;
    }

    /** 同步成功，置为已同步（后续由清理任务回收） */
    private void markDone(EsSyncOutbox item) {
        EsSyncOutbox upd = new EsSyncOutbox();
        upd.setId(item.getId());
        upd.setStatus(1);
        outboxMapper.updateById(upd);
    }

    /**
     * 同步失败：重试次数 +1 并按指数退避推迟下次重试。
     * 保持 status=0，ES 恢复后由轮询继续追平，不会丢。
     */
    private void markRetry(EsSyncOutbox item, Exception e) {
        int retry = (item.getRetryCount() == null ? 0 : item.getRetryCount()) + 1;
        // 2,4,8,...,封顶 maxBackoffSeconds；ES 抖动时避免打爆
        long delay = Math.min(1L << Math.min(retry, 8), Math.max(maxBackoffSeconds, 1));
        log.warn("ES 同步失败(第 {} 次，{}s 后重试), bizType={}, bizId={}, op={}: {}",
                retry, delay, item.getBizType(), item.getBizId(), item.getOp(), e.getMessage());
        EsSyncOutbox upd = new EsSyncOutbox();
        upd.setId(item.getId());
        upd.setRetryCount(retry);
        upd.setNextRetryAt(LocalDateTime.now().plusSeconds(delay));
        outboxMapper.updateById(upd);
    }

    /** 回收已同步记录，防止发件箱无限增长（只删完成态，待同步记录永不清理） */
    @Scheduled(cron = "${campus.es.outbox.cleanup-cron:0 40 4 * * ?}")
    public void cleanupDone() {
        int deleted = outboxMapper.delete(new LambdaQueryWrapper<EsSyncOutbox>()
                .eq(EsSyncOutbox::getStatus, 1)
                .lt(EsSyncOutbox::getUpdateTime, LocalDateTime.now().minusDays(retainDays)));
        if (deleted > 0) {
            log.info("清理 ES 同步发件箱已同步记录 {} 条", deleted);
        }
    }
}
