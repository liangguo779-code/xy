package com.campus.ai.config;

import com.campus.ai.assistant.CampusRagAssistant;
import com.campus.ai.memory.MemoryStore;
import com.campus.ai.tool.CalendarTool;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.bgesmallzh.BgeSmallZhEmbeddingModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import dev.langchain4j.model.scoring.ScoringModel;
import dev.langchain4j.model.scoring.onnx.OnnxScoringModel;
import dev.langchain4j.service.AiServices;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;
import java.util.List;

/**
 * LangChain4j Bean 配置类：装配对话模型、Embedding 模型、重排序模型和 RAG 助手。
 *
 * <ul>
 *   <li>对话模型：通过 {@link OpenAiChatModel} 对接 OpenAI 兼容接口（DeepSeek）</li>
 *   <li>Embedding：BGE small-zh（512 维中文模型），首次构建时由 langchain4j 从 HuggingFace 下载</li>
 *   <li>重排序：BGE reranker（cross-encoder），以本地 ONNX 模型 + tokenizer 加载</li>
 * </ul>
 *
 * <p>向量存储由 {@code VectorStoreFacade} 使用 ES Java Client 直接管理（详见其 Javadoc）。
 *
 * <p>如果没有这个文件：
 * <ul>
 *   <li>ChatModel、EmbeddingModel、ScoringModel 等核心 Bean 无法创建，应用启动失败</li>
 *   <li>CampusRagAssistant 无法通过 AiServices 构建，LLM 对话和工具调用不可用</li>
 *   <li>流式对话模型（streamingChatModel）缺失，SSE 流式输出将无法工作</li>
 * </ul>
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(AiProperties.class)
public class LangChain4jConfig {

    private final AiProperties props;

    public LangChain4jConfig(AiProperties props) {
        this.props = props;
    }

    @Bean
    public EmbeddingModel embeddingModel() {
        AiProperties.Vector v = props.getVector();
        log.info("Loading BGE small-zh embedder (dim={}, hf={})",
                v.getEmbeddingDim(), v.getEmbeddingHfRepo());
        // BgeSmallZhEmbeddingModel 没有基于 Path 的构造函数；
        // 模型由 langchain4j embeddings-bge-small-zh 模块在首次使用时自动下载并缓存。
        return new BgeSmallZhEmbeddingModel();
    }

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
     * 流式对话模型 —— 调度器用它实现"打字机"效果（逐 token SSE 事件），而非等待完整回答。
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

    /**
     * BGE cross-encoder 重排序模型。首次使用时从 HuggingFace 下载并缓存到本地。
     * 如果模型无法下载，首次请求返回 null —— 调度器会跳过重排序而不是崩溃。
     */
    @Bean
    public ScoringModel scoringModel() {
        AiProperties.Vector v = props.getVector();
        Path modelDir = HuggingFaceModelLoader.underHome(
                props.getHome(), "models", "bge-reranker-base");
        Path modelFile = modelDir.resolve("model.onnx");
        Path tokenizerFile = modelDir.resolve("tokenizer.json");
        // 如果本地已有文件（download-models.sh 已下载），直接使用，不触发网络下载
        if (java.nio.file.Files.exists(modelFile) && java.nio.file.Files.exists(tokenizerFile)) {
            try {
                log.info("Loading BGE reranker (cross-encoder) from {}", modelDir);
                return new OnnxScoringModel(
                        modelFile.toString(), tokenizerFile.toString());
            } catch (Exception e) {
                log.warn("BGE reranker 加载失败: {} — 重排序将跳过", e.getMessage());
                return new NoOpScoringModel();
            }
        }
        log.warn("⚠️  BGE reranker 模型不存在 ({}), 跳过重排序", modelDir);
        log.warn("   可运行: bash scripts/download-models.sh");
        return new NoOpScoringModel();
    }

    /**
     * 通过 AiServices 构建 CampusRagAssistant，ChatMemory 由 MySQL 支持。
     * 工具（CalendarTool 等）在此注册，使 LLM 能够调用。
     *
     * <p>注意：不能用 {@code List<Object>} 注入所有 Bean，否则会把 Controller 等也当工具注入，
     * 导致循环依赖。直接注入已知的工具类。
     */
    @Bean
    public CampusRagAssistant campusRagAssistant(OpenAiChatModel chatModel,
                                                  MemoryStore memoryStore,
                                                  CalendarTool calendarTool) {
        log.info("Building CampusRagAssistant with tool: CalendarTool");
        return AiServices.builder(CampusRagAssistant.class)
                .chatModel(chatModel)
                .chatMemoryProvider(memoryId ->
                        MessageWindowChatMemory.builder()
                                .id(memoryId)
                                .maxMessages(20)
                                .chatMemoryStore(memoryStore)
                                .build())
                .tools(calendarTool)
                .build();
    }

    /** 重排序模型无法下载时的空安全兜底实现。 */
    private static class NoOpScoringModel implements ScoringModel {
        @Override
        public dev.langchain4j.model.output.Response<List<Double>> scoreAll(
                List<dev.langchain4j.data.segment.TextSegment> segments, String query) {
            // 返回恒等分数 —— 保持原始排序，不进行重排序。
            List<Double> scores = new java.util.ArrayList<>();
            for (int i = 0; i < segments.size(); i++) scores.add((double) -i);
            return dev.langchain4j.model.output.Response.from(scores);
        }
    }
}
