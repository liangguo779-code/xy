package com.campus.ai.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RebuildStatusRegistryConfig {

    @Bean
    public RebuildStatusRegistry rebuildStatusRegistry() {
        RebuildStatusRegistry r = new RebuildStatusRegistry();
        r.reset();
        return r;
    }
}
