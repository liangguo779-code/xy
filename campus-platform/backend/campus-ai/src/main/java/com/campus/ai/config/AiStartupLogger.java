package com.campus.ai.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Logs a one-line startup hint so the operator knows the AI models are loaded
 * lazily on the first chat request (BGE small-zh ~ 90 MB + bge-reranker-base
 * ~ 280 MB download on first cold start).
 */
@Slf4j
@Configuration
public class AiStartupLogger {

    @Bean
    public ApplicationRunner aiStartupHint() {
        return args -> {
            String home = System.getProperty("user.home");
            log.info("=".repeat(72));
            log.info("Campus AI is up. BGE embedding + reranker are loaded on first chat request.");
            log.info("Models will be cached under {}/.cache/huggingface/ (or HF_HOME if set).", home);
            log.info("Set HF_HUB_OFFLINE=1 and pre-stage models to skip the first-request download.");
            log.info("=".repeat(72));
        };
    }
}
