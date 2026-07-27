package com.campus.ai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Root configuration for the in-process LangChain4j AI implementation. The previous
 * build read {@code ai.service.url} to call an external Python service; that path is
 * removed and these properties are now authoritative.
 */
@Data
@ConfigurationProperties(prefix = "campus.ai")
public class AiProperties {

    /** Home directory for AI runtime data (knowledge dir, BM25 snapshot). */
    private String home = "./runtime";

    private Knowledge knowledge = new Knowledge();
    private Vector vector = new Vector();
    private Llm llm = new Llm();
    private Rebuild rebuild = new Rebuild();

    @Data
    public static class Knowledge {
        /** Writable knowledge directory. Seeded from classpath on first start. */
        private String dir;
        /** Max upload size (Spring DataSize: accepts "20MB", "50mb", bytes). Default 20 MB. */
        private org.springframework.util.unit.DataSize maxFileSize =
                org.springframework.util.unit.DataSize.ofMegabytes(20);
        /** Allowed suffixes (comma-separated). */
        private String allowedSuffixes = ".md,.txt,.pdf,.docx,.doc";
        /** Markdown chunk size (chars). */
        private int chunkSize = 500;
        /** Markdown chunk overlap (chars). */
        private int chunkOverlap = 50;
        /** Rerank score threshold. Lower = more permissive. */
        private double scoreThreshold = 0.8;
        /** RRF k constant. */
        private int bm25K = 60;
    }

    @Data
    public static class Vector {
        /** ES index name for vector storage. */
        private String indexName = "campus_knowledge";
        /** Embedding model identifier (logged only — model class is wired in LangChain4jConfig). */
        private String embeddingModel = "bge-small-zh";
        /** Embedding vector dimension. Must match the chosen embedder (512 for bge-small-zh). */
        private int embeddingDim = 512;
        /** Hugging Face repo for the BGE small-zh embedding model. */
        private String embeddingHfRepo = "Xenova/bge-small-zh";
        /** Local directory the embedder reads from; populated on first start. */
        private String embeddingModelPath;
        private int rerankTopk = 5;
        private int retrieveTopk = 10;
        /** Hugging Face repo for the BGE reranker (cross-encoder). */
        private String rerankerHfRepo = "Xenova/bge-reranker-base";
        /** Local directory the reranker reads from; populated on first start. */
        private String rerankerModelPath;
        /** Cross-encoder max sequence length. */
        private int rerankerMaxSeqLength = 512;
    }

    @Data
    public static class Llm {
        private String provider = "openai-compatible";
        private String baseUrl;
        private String apiKey;
        private String model = "deepseek-chat";
        private double temperature = 0.3;
        /** Read timeout. Accepts "60s", "1m" (Spring Duration format). */
        private java.time.Duration timeout = java.time.Duration.ofSeconds(60);
    }

    @Data
    public static class Rebuild {
        private int maxConcurrent = 1;
    }
}
