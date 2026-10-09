package com.campus.ai.config;

import com.campus.ai.assistant.CampusRagAssistant;
import com.campus.ai.memory.MemoryStore;
import com.campus.ai.tool.CalendarTool;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import dev.langchain4j.service.AiServices;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * CampusRagAssistant 配置 - 通过 AiServices 构建
 * 与 CampusRagAssistant.java 对应
 */
@Slf4j
@Configuration
public class CampusRagAssistantConfig {

    /**
     * 构建 CampusRagAssistant
     * - 注入对话模型
     * - 注入记忆存储（MySQL 持久化）
     * - 注入工具（CalendarTool 等）
     *
     * 注意：不能用 List<Object> 注入所有 Bean，否则会把 Controller 等也当工具注入
     */
    @Bean
    public CampusRagAssistant campusRagAssistant(
            OpenAiChatModel chatModel,
            OpenAiStreamingChatModel streamingChatModel,
            MemoryStore memoryStore,
            CalendarTool calendarTool) {

        log.info("Building CampusRagAssistant with tool: CalendarTool");

        return AiServices.builder(CampusRagAssistant.class)
                .chatModel(chatModel)                    // 对话模型（同步方法用）
                .streamingChatModel(streamingChatModel)  // 流式模型（TokenStream 方法用）
                .chatMemoryProvider(memoryId ->          // 记忆（MySQL 持久化）
                        MessageWindowChatMemory.builder()
                                .id(memoryId)
                                .maxMessages(20)
                                .chatMemoryStore(memoryStore)
                                .build())
                .tools(calendarTool)                     // 工具
                .build();
    }
}
