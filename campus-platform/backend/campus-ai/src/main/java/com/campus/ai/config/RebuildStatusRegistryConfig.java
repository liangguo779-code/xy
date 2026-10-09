package com.campus.ai.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 注册重建状态注册表 Bean，构造后即置为初始状态。 */
@Configuration
public class RebuildStatusRegistryConfig {

    @Bean
    public RebuildStatusRegistry rebuildStatusRegistry() {
        RebuildStatusRegistry r = new RebuildStatusRegistry();
        r.reset();
        return r;
    }
}
