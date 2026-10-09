package com.campus.common.moderation;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.common.entity.SensitiveWord;
import com.campus.common.mapper.SensitiveWordMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * 敏感词库匹配（本地关键词过滤引擎，sensitive_word 表即全部词库）。
 * 内存缓存 30 秒 TTL（同 DisabledFilesCache 模式），词库变更后调用
 * {@link #invalidate()} 立即生效。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SensitiveWordService {

    /** 缓存有效期（毫秒） */
    private static final long TTL_MS = 30_000L;

    private final SensitiveWordMapper mapper;
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private List<SensitiveWord> cached = List.of();
    private long loadedAt = 0;

    /** 一次命中：词、类目、处置级别。 */
    public record Hit(String word, String category, int level) {}

    /** 返回文本命中的全部自定义敏感词（区分大小写无关，子串匹配）。 */
    public List<Hit> match(String text) {
        List<Hit> out = new ArrayList<>();
        if (text == null || text.isEmpty()) return out;
        String lower = text.toLowerCase(java.util.Locale.ROOT);
        for (SensitiveWord w : snapshot()) {
            if (w.getWord() == null || w.getWord().isEmpty()) continue;
            if (lower.contains(w.getWord().toLowerCase(java.util.Locale.ROOT))) {
                out.add(new Hit(w.getWord(), w.getCategory(), w.getLevel() == null ? 1 : w.getLevel()));
            }
        }
        return out;
    }

    /** 清空缓存，下次 match 强制读库。 */
    public void invalidate() {
        lock.writeLock().lock();
        try {
            loadedAt = 0;
        } finally {
            lock.writeLock().unlock();
        }
    }

    private List<SensitiveWord> snapshot() {
        long now = System.currentTimeMillis();
        lock.readLock().lock();
        try {
            if (now - loadedAt < TTL_MS && !cached.isEmpty()) return cached;
        } finally {
            lock.readLock().unlock();
        }
        reload();
        lock.readLock().lock();
        try {
            return cached;
        } finally {
            lock.readLock().unlock();
        }
    }

    private void reload() {
        lock.writeLock().lock();
        try {
            cached = mapper.selectList(new LambdaQueryWrapper<SensitiveWord>()
                    .eq(SensitiveWord::getEnabled, 1));
            loadedAt = System.currentTimeMillis();
        } catch (Exception e) {
            log.warn("加载敏感词库失败: {}", e.getMessage());
            // 保留旧缓存继续服务，避免 DB 抖动导致词库瞬间空窗
            loadedAt = System.currentTimeMillis();
        } finally {
            lock.writeLock().unlock();
        }
    }
}
