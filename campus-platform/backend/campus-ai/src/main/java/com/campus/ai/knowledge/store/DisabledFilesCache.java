package com.campus.ai.knowledge.store;

import com.campus.ai.config.AiProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * 对应 Python 服务的 {@code _config.json} + 30 秒内存缓存。
 * 被禁用的文件会从向量检索和 BM25 检索中排除。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DisabledFilesCache {

    /** 内存缓存有效期（毫秒），过期后重新读盘，兼顾一致性与检索路径的读盘开销。 */
    private static final long TTL_MS = 30_000L;
    /** 持久化配置文件名，存放于知识目录下。 */
    private static final String CONFIG_FILE = "_config.json";

    private final AiProperties props;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 读写锁：检索线程并发读缓存，reload/toggle 时独占写。 */
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    /** 当前被禁用的文件名集合。 */
    private Set<String> disabled = Set.of();
    /** 上次加载时间戳，配合 TTL 判断缓存是否过期。 */
    private long loadedAt = 0;

    /** 启动时预加载一次禁用列表。 */
    @PostConstruct
    void init() {
        reload();
    }

    /**
     * 获取禁用文件名集合。缓存未过期且非空时直接返回（空集合也按需重读，
     * 避免"全部启用"状态下磁盘变更迟迟不可见）；否则重新加载。
     */
    public Set<String> getDisabled() {
        long now = System.currentTimeMillis();
        lock.readLock().lock();
        try {
            if (now - loadedAt < TTL_MS && !disabled.isEmpty()) {
                return disabled;
            }
        } finally {
            lock.readLock().unlock();
        }
        reload();
        lock.readLock().lock();
        try {
            return disabled;
        } finally {
            lock.readLock().unlock();
        }
    }

    /** 切换指定文件的禁用状态并落盘，返回切换后的启用状态（true=已启用）。 */
    public boolean toggle(String filename) {
        Set<String> set = new HashSet<>(getDisabled());
        boolean enabled;
        if (set.contains(filename)) {
            set.remove(filename);
            enabled = true;
        } else {
            set.add(filename);
            enabled = false;
        }
        save(set);
        invalidate();
        return enabled;
    }

    /** 置空加载时间戳，强制下次 getDisabled 时重新读盘。 */
    public void invalidate() {
        lock.writeLock().lock();
        try {
            loadedAt = 0;
        } finally {
            lock.writeLock().unlock();
        }
    }

    private void reload() {
        lock.writeLock().lock();
        try {
            Path cfg = configPath();
            if (!Files.exists(cfg)) {
                disabled = Set.of();
                loadedAt = System.currentTimeMillis();
                return;
            }
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> map = objectMapper.readValue(cfg.toFile(), Map.class);
                Object list = map.get("disabled");
                if (list instanceof Collection<?> coll) {
                    Set<String> s = new HashSet<>();
                    for (Object o : coll) if (o != null) s.add(o.toString());
                    disabled = s;
                } else {
                    disabled = Set.of();
                }
                loadedAt = System.currentTimeMillis();
            } catch (IOException e) {
                log.warn("Failed to read {}: {}", cfg, e.getMessage());
                disabled = Set.of();
                loadedAt = System.currentTimeMillis();
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    private void save(Set<String> set) {
        Path cfg = configPath();
        try {
            Files.createDirectories(cfg.getParent());
            objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValue(cfg.toFile(), Map.of("disabled", set));
        } catch (IOException e) {
            throw new RuntimeException("Failed to write " + cfg, e);
        }
    }

    private Path configPath() {
        return Path.of(props.getKnowledge().getDir(), CONFIG_FILE);
    }
}
