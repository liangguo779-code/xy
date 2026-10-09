package com.campus.ai.config;

import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 对话模型配置
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
@EnableConfigurationProperties(AiProperties.class)
public class ModelConfig {

    private final AiProperties props;

    /**
     * 同步对话模型
     */
    @Bean
    public OpenAiChatModel chatModel() {
        AiProperties.Llm l = props.getLlm();
        if (l.getApiKey() == null || l.getApiKey().isBlank()) {
            log.warn("campus.ai.llm.api-key is empty — chat will fail until OPENAI_API_KEY is set");
        }
        return OpenAiChatModel.builder()
                .baseUrl(l.getBaseUrl())
                .apiKey(l.getApiKey() == null ? "missing" : l.getApiKey())
                .modelName(l.getModel())
                .temperature(l.getTemperature())
                .timeout(l.getTimeout())
                .build();
    }

    /**
     * 流式对话模型 —— 实现"打字机"效果（逐 token SSE 事件）
     */
    @Bean
    public OpenAiStreamingChatModel streamingChatModel() {
        AiProperties.Llm l = props.getLlm();
        return OpenAiStreamingChatModel.builder()
                .baseUrl(l.getBaseUrl())
                .apiKey(l.getApiKey() == null ? "missing" : l.getApiKey())
                .modelName(l.getModel())
                .temperature(l.getTemperature())
                .timeout(l.getTimeout())
                .build();
    }
}
