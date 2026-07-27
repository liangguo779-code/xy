package com.campus.ai.config;

import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

/**
 * In-memory singleton that mirrors the Python service's
 * {@code _rebuild_status} dict. The frontend polls
 * {@code GET /api/admin/knowledge/rebuild/status} to read it.
 */
public class RebuildStatusRegistry {

    private final ReentrantLock lock = new ReentrantLock();
    private final Map<String, Object> status = new LinkedHashMap<>();

    public void reset() {
        lock.lock();
        try {
            status.clear();
            status.put("running", true);
            status.put("progress", 0);
            status.put("completed", false);
            status.put("result", null);
            status.put("error", null);
        } finally {
            lock.unlock();
        }
    }

    public void updateProgress(int progress) {
        lock.lock();
        try {
            status.put("progress", progress);
        } finally {
            lock.unlock();
        }
    }

    public void complete(int docs, int chunks) {
        lock.lock();
        try {
            status.put("running", false);
            status.put("progress", 100);
            status.put("completed", true);
            status.put("result", Map.of("docs", docs, "chunks", chunks));
        } finally {
            lock.unlock();
        }
    }

    public void error(String message) {
        lock.lock();
        try {
            status.put("running", false);
            status.put("completed", false);
            status.put("error", message);
        } finally {
            lock.unlock();
        }
    }

    public Map<String, Object> snapshot() {
        lock.lock();
        try {
            return new LinkedHashMap<>(status);
        } finally {
            lock.unlock();
        }
    }
}
