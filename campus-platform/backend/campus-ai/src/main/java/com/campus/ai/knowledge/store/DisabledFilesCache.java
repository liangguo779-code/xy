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
 * Mirrors the Python service's {@code _config.json} + 30-second in-memory cache. Disabled
 * files are excluded from both vector and BM25 retrieval.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DisabledFilesCache {

    private static final long TTL_MS = 30_000L;
    private static final String CONFIG_FILE = "_config.json";

    private final AiProperties props;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private Set<String> disabled = Set.of();
    private long loadedAt = 0;

    @PostConstruct
    void init() {
        reload();
    }

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
