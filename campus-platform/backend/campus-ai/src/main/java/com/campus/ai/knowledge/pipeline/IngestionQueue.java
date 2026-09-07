package com.campus.ai.knowledge.pipeline;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * 索引构建的后台执行器。替代 FastAPI 的 {@code BackgroundTasks}。
 * 线程池大小由 {@code campus.ai.rebuild.max-concurrent} 限制（默认 1）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IngestionQueue {

    private final com.campus.ai.config.AiProperties props;
    private ExecutorService executor;

    @PostConstruct
    void init() {
        int n = Math.max(1, props.getRebuild().getMaxConcurrent());
        executor = Executors.newFixedThreadPool(n, r -> {
            Thread t = new Thread(r, "ai-ingest");
            t.setDaemon(true);
            return t;
        });
        log.info("IngestionQueue started with {} thread(s)", n);
    }

    public Future<?> submit(Runnable task) {
        return executor.submit(task);
    }

    @PreDestroy
    void shutdown() {
        if (executor != null) {
            executor.shutdown();
        }
    }
}
