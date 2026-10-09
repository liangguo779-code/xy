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

    /** 互斥锁：重建线程写状态、HTTP 线程读快照，避免并发读写 map。 */
    private final ReentrantLock lock = new ReentrantLock();
    /** 状态快照：running / progress / completed / result / error。 */
    private final Map<String, Object> status = new LinkedHashMap<>();

    /** 重置为"进行中"初始状态（progress=0）。 */
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

    /** 更新进度百分比（0-100）。 */
    public void updateProgress(int progress) {
        lock.lock();
        try {
            status.put("progress", progress);
        } finally {
            lock.unlock();
        }
    }

    /** 标记完成并记录文档数/分块数统计及清理的孤儿来源数。 */
    public void complete(int docs, int chunks, int removedSources) {
        lock.lock();
        try {
            status.put("running", false);
            status.put("progress", 100);
            status.put("completed", true);
            status.put("result", Map.of("docs", docs, "chunks", chunks, "removed", removedSources));
        } finally {
            lock.unlock();
        }
    }

    /** 标记失败并记录错误信息。 */
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

    /** 返回状态的防御性拷贝，防止调用方篡改内部 map。 */
    public Map<String, Object> snapshot() {
        lock.lock();
        try {
            return new LinkedHashMap<>(status);
        } finally {
            lock.unlock();
        }
    }
}
