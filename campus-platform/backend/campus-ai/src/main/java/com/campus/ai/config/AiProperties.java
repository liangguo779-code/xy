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

    /** 知识库相关配置。 */
    private Knowledge knowledge = new Knowledge();
    /** 向量检索相关配置。 */
    private Vector vector = new Vector();
    /** 大模型接入配置。 */
    private Llm llm = new Llm();
    /** 索引重建并发配置。 */
    private Rebuild rebuild = new Rebuild();

    @Data
    public static class Knowledge {
        /** 可写的知识库目录。首次启动时从 classpath 初始化。 */
        private String dir;
        /** 最大上传大小（Spring DataSize 格式：支持 "20MB"、"50mb"、字节数）。默认 20 MB。 */
        private org.springframework.util.unit.DataSize maxFileSize =
                org.springframework.util.unit.DataSize.ofMegabytes(20);
        /** 允许的文件后缀（逗号分隔）。不含 .doc —— DocumentConverter 仅支持 md/txt/pdf/docx。 */
        private String allowedSuffixes = ".md,.txt,.pdf,.docx";
        /** Markdown 分块大小（字符数）。 */
        private int chunkSize = 500;
        /** Markdown 分块重叠（字符数）：超长块切分时并入下一块开头的尾部长度。 */
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
        /** Embedding 模型标识（仅用于日志 —— 模型类在 EmbeddingConfig 中装配为 BgeSmallZhEmbeddingModel）。 */
        private String embeddingModel = "Xenova/bge-small-zh";
        /** Embedding 向量维度。必须与所选 Embedding 模型匹配（bge-small-zh 为 512）。 */
        private int embeddingDim = 512;
        /** BGE small-zh Embedding 模型的 HuggingFace 仓库地址。 */
        private String embeddingHfRepo = "Xenova/bge-small-zh";
        /** Embedding 模型的本地目录；首次启动时填充。 */
        private String embeddingModelPath;
        /** 重排序后最终保留的片段数。 */
        private int rerankTopk = 5;
        /** 单次向量/BM25 检索的候选数（实际会多取以补偿父分块过滤）。 */
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
        /** 提供商类型，目前固定为 openai-compatible 协议。 */
        private String provider = "openai-compatible";
        /** OpenAI 兼容接口地址。 */
        private String baseUrl;
        /** API 密钥。 */
        private String apiKey;
        /** 模型名称。 */
        private String model = "deepseek-chat";
        /** 采样温度，越低回答越稳定，问答场景取 0.3。 */
        private double temperature = 0.3;
        /** 读取超时。支持 "60s"、"1m"（Spring Duration 格式）。 */
        private java.time.Duration timeout = java.time.Duration.ofSeconds(60);
    }

    @Data
    public static class Rebuild {
        /** 入库/重建线程池并发数，默认 1 串行执行避免索引写冲突。 */
        private int maxConcurrent = 1;
    }
}
