package com.campus.ai.config;

import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.bgesmallzh.BgeSmallZhEmbeddingModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.scoring.ScoringModel;
import dev.langchain4j.model.scoring.onnx.OnnxScoringModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

/**
 * Wires the LangChain4j beans (chat model, embedding model, scoring model).
 *
 * <ul>
 *   <li>Chat: OpenAI-compatible (DeepSeek) via {@link OpenAiChatModel}.</li>
 *   <li>Embedding: BGE small-zh (512-dim Chinese). The model is downloaded by langchain4j from
 *       Hugging Face Hub on first construction.</li>
 *   <li>Scoring: BGE reranker (cross-encoder), loaded as a local ONNX model + tokenizer.</li>
 * </ul>
 *
 * <p>Vector store is handled by {@code VectorStoreFacade} using the raw Elasticsearch
 * Java Client directly (see the Javadoc there for why).
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
        // BgeSmallZhEmbeddingModel has no Path-based constructor; the model is downloaded
        // and cached by the langchain4j embeddings-bge-small-zh module on first use.
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
     * BGE cross-encoder reranker. Downloads from HuggingFace on first use and
     * caches locally. Returns null on first request if the model can't be
     * downloaded — the orchestrator skips reranking instead of crashing.
     *
     * The @Lazy annotation here is best-effort; because RagOrchestrator injects
     * ScoringModel directly, Spring still creates this bean eagerly at context
     * init. The real lazy-load happens via {@link #lazyScoringModel()} below,
     * which wraps this method in a null-on-failure guard.
     */
    @Bean
    public ScoringModel scoringModel() {
        AiProperties.Vector v = props.getVector();
        Path modelDir = HuggingFaceModelLoader.underHome(
                props.getHome(), "models", "bge-reranker-base");
        Path modelFile = modelDir.resolve("model.onnx");
        Path tokenizerFile = modelDir.resolve("tokenizer.json");
        // 如果本地已有文件（download-models.sh 已下载），直接用；不触发网络下载
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

    /** Null-safe fallback when the reranker model can't be downloaded. */
    private static class NoOpScoringModel implements ScoringModel {
        @Override
        public dev.langchain4j.model.output.Response<List<Double>> scoreAll(
                List<dev.langchain4j.data.segment.TextSegment> segments, String query) {
            // Return identity scores — rank order preserved, no reranking.
            List<Double> scores = new java.util.ArrayList<>();
            for (int i = 0; i < segments.size(); i++) scores.add((double) -i);
            return dev.langchain4j.model.output.Response.from(scores);
        }
    }
}
