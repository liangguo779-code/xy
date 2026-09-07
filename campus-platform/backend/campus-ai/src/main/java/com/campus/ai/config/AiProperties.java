package com.campus.ai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * AI 模块根配置类。之前的版本通过 {@code ai.service.url} 调用外部 Python 服务；
 * 该路径已移除，现在由这些配置项作为唯一配置源。
 */
@Data
@ConfigurationProperties(prefix = "campus.ai")
public class AiProperties {

    /** AI 运行时数据的根目录（知识库目录、BM25 快照）。 */
    private String home = "./runtime";

    private Knowledge knowledge = new Knowledge();
    private Vector vector = new Vector();
    private Llm llm = new Llm();
    private Rebuild rebuild = new Rebuild();

    @Data
    public static class Knowledge {
        /** 可写的知识库目录。首次启动时从 classpath 初始化。 */
        private String dir;
        /** 最大上传大小（Spring DataSize 格式：支持 "20MB"、"50mb"、字节数）。默认 20 MB。 */
        private org.springframework.util.unit.DataSize maxFileSize =
                org.springframework.util.unit.DataSize.ofMegabytes(20);
        /** 允许的文件后缀（逗号分隔）。 */
        private String allowedSuffixes = ".md,.txt,.pdf,.docx,.doc";
        /** Markdown 分块大小（字符数）。 */
        private int chunkSize = 500;
        /** Markdown 分块重叠（字符数）。 */
        private int chunkOverlap = 50;
        /**
         * Cross-encoder 分数阈值（取负余弦，范围 [-1, 0]；越低越好）。
         * 重排序分数高于此值（即匹配较差）时触发一次查询改写 + 重试。
         */
        private double scoreThreshold = -0.2;
        /**
         * 最高重排序分数 ≤ 此值时判定为"高"置信度。默认目标为余弦相似度 ~0.6 或更高。
         */
        private double highConfidenceThreshold = -0.6;
        /**
         * 最高重排序分数 ≤ 此值（但高于 {@link #highConfidenceThreshold}）时判定为"中"置信度。
         * 默认目标为余弦相似度 ~0.3。
         */
        private double mediumConfidenceThreshold = -0.3;
        /** RRF 融合常数 k。 */
        private int bm25K = 60;
    }

    @Data
    public static class Vector {
        /** ES 向量存储索引名。 */
        private String indexName = "campus_knowledge";
        /** Embedding 模型标识（仅用于日志 —— 模型类在 LangChain4jConfig 中装配）。 */
        private String embeddingModel = "bge-small-zh";
        /** Embedding 向量维度。必须与所选 Embedding 模型匹配（bge-small-zh 为 512）。 */
        private int embeddingDim = 512;
        /** BGE small-zh Embedding 模型的 HuggingFace 仓库地址。 */
        private String embeddingHfRepo = "Xenova/bge-small-zh";
        /** Embedding 模型的本地目录；首次启动时填充。 */
        private String embeddingModelPath;
        private int rerankTopk = 5;
        private int retrieveTopk = 10;
        /** BGE reranker（cross-encoder）的 HuggingFace 仓库地址。 */
        private String rerankerHfRepo = "Xenova/bge-reranker-base";
        /** 重排序模型的本地目录；首次启动时填充。 */
        private String rerankerModelPath;
        /** Cross-encoder 最大序列长度。 */
        private int rerankerMaxSeqLength = 512;
    }

    @Data
    public static class Llm {
        private String provider = "openai-compatible";
        private String baseUrl;
        private String apiKey;
        private String model = "deepseek-chat";
        private double temperature = 0.3;
        /** 读取超时。支持 "60s"、"1m"（Spring Duration 格式）。 */
        private java.time.Duration timeout = java.time.Duration.ofSeconds(60);
    }

    @Data
    public static class Rebuild {
        private int maxConcurrent = 1;
    }
}
