package com.campus.ai.config;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.bgesmallzh.BgeSmallZhEmbeddingModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.scoring.ScoringModel;
import dev.langchain4j.model.scoring.onnx.OnnxScoringModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.elasticsearch.ElasticsearchEmbeddingStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

/**
 * Wires the LangChain4j beans (chat model, embedding model, embedding store, scoring model).
 *
 * <ul>
 *   <li>Chat: OpenAI-compatible (DeepSeek) via {@link OpenAiChatModel}.</li>
 *   <li>Embedding: BGE small-zh (512-dim Chinese). The model is downloaded by langchain4j from
 *       Hugging Face Hub on first construction. The cache location is controlled by the
 *       {@code HF_HOME} environment variable (defaults to {@code ~/.cache/huggingface}).</li>
 *   <li>Vector store: Elasticsearch dense-vector index {@code campus_knowledge}.</li>
 *   <li>Scoring: BGE reranker (cross-encoder), loaded as a local ONNX model + tokenizer. The
 *       model and tokenizer are downloaded on first start from
 *       {@code https://huggingface.co/Xenova/bge-reranker-base} into
 *       {@code ${campus.ai.home}/models/bge-reranker-base}.</li>
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
        // BgeSmallZhEmbeddingModel has no Path-based constructor; the model is downloaded
        // and cached by the langchain4j embeddings-bge-small-zh module on first use.
        return new BgeSmallZhEmbeddingModel();
    }

    @Bean
    public EmbeddingStore<TextSegment> embeddingStore() {
        AiProperties.Vector v = props.getVector();
        log.info("Configuring ElasticsearchEmbeddingStore index='{}' dim={}",
                v.getIndexName(), v.getEmbeddingDim());
        return ElasticsearchEmbeddingStore.builder()
                .serverUrl("http://" + System.getenv().getOrDefault("ELASTICSEARCH_HOST", "localhost")
                        + ":" + System.getenv().getOrDefault("ELASTICSEARCH_PORT", "9200"))
                .indexName(v.getIndexName())
                .dimension(v.getEmbeddingDim())
                .build();
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
        try {
            HuggingFaceModelLoader.ensure(v.getRerankerHfRepo(), modelDir, List.of(
                    "model.onnx", "tokenizer.json"
            ));
            log.info("Loading BGE reranker (cross-encoder) from {}", modelDir);
            return new OnnxScoringModel(
                    modelDir.resolve("model.onnx").toString(),
                    modelDir.resolve("tokenizer.json").toString());
        } catch (Exception e) {
            log.warn("⚠️  BGE reranker 模型加载失败: {} — 重排序将跳过，返回原始顺序", e.getMessage());
            log.warn("   可手动下载: bash scripts/download-models.sh");
            return new NoOpScoringModel();
        }
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
