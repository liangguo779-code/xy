package com.campus.common.moderation;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 内容安全治理配置（campus.moderation.*）：AI 审核判定阈值。
 * 关键词过滤为纯本地词库（sensitive_word 表），无外部引擎配置。
 */
@Data
@Component
@ConfigurationProperties(prefix = "campus.moderation")
public class ModerationProperties {

    /** AI 判违规且置信度 ≥ 该阈值才自动拦截；低于则转人审 */
    private double aiVerifyThreshold = 0.90d;

    /** AI 判合规且置信度 ≥ 该阈值才自动放行；低于则转人审 */
    private double aiClearThreshold = 0.85d;
}
