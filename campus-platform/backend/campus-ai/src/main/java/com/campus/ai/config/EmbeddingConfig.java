package com.campus.ai.config;

import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.bgesmallzh.BgeSmallZhEmbeddingModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 嵌入模型配置
 * BGE small-zh：512 维中文嵌入模型
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class EmbeddingConfig {

    private final AiProperties props;

    /**
     * 嵌入模型（用于向量检索）
     * 首次使用时自动从 HuggingFace 下载并缓存。
     * 注意：实际加载的是内置 BgeSmallZhEmbeddingModel（Xenova/bge-small-zh，512 维），
     * 配置项 embedding-model / embedding-dim 必须与之一致（仅用于日志与索引维度校验）。
     */
    @Bean
    public EmbeddingModel embeddingModel() {
        AiProperties.Vector v = props.getVector();
        if (v.getEmbeddingDim() != 512) {
            log.warn("embedding-dim={} 与内置 BgeSmallZhEmbeddingModel（512 维）不一致，ES 向量映射可能出错",
                    v.getEmbeddingDim());
        }
        log.info("Loading BGE small-zh embedder (model={}, dim={}, hf={})",
                v.getEmbeddingModel(), v.getEmbeddingDim(), v.getEmbeddingHfRepo());
        return new BgeSmallZhEmbeddingModel();
    }
}
