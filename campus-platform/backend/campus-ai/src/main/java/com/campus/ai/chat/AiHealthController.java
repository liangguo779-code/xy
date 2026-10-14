package com.campus.ai.chat;

import com.campus.ai.config.AiProperties;
import com.campus.ai.retrieval.VectorStoreFacade;
import com.campus.common.result.R;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 轻量级健康检查端点，对应之前 Python 服务的 {@code /health}。
 * 报告当前配置的 LLM、向量存储、Embedding 模型和关键词检索通道信息。
 */
@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AiHealthController {

    private final AiProperties props;
    private final VectorStoreFacade vectorStore;

    /** 汇总 LLM/向量库/关键词检索/知识目录的配置与规模信息，供运维快速核对环境。 */
    @GetMapping("/health")
    public R<Map<String, Object>> health() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "ok");

        Map<String, Object> checks = new LinkedHashMap<>();
        Map<String, Object> llm = new LinkedHashMap<>();
        llm.put("provider", props.getLlm().getProvider());
        llm.put("baseUrl", props.getLlm().getBaseUrl());
        llm.put("model", props.getLlm().getModel());
        llm.put("apiKeyConfigured", props.getLlm().getApiKey() != null && !props.getLlm().getApiKey().isBlank());
        checks.put("llm", llm);

        Map<String, Object> vector = new LinkedHashMap<>();
        vector.put("index", props.getVector().getIndexName());
        vector.put("dimension", props.getVector().getEmbeddingDim());
        vector.put("documentCount", vectorStore.count());
        checks.put("vectorStore", vector);

        // 关键词检索与向量共用 ES 索引（text 字段 IK 分词），报告索引名与规模便于核对。
        Map<String, Object> keyword = new LinkedHashMap<>();
        keyword.put("index", props.getVector().getIndexName());
        keyword.put("analyzer", "ik_max_word/ik_smart");
        keyword.put("documentCount", vectorStore.count());
        checks.put("keyword", keyword);

        Map<String, Object> knowledge = new LinkedHashMap<>();
        knowledge.put("dir", props.getKnowledge().getDir());
        checks.put("knowledge", knowledge);

        body.put("checks", checks);
        return R.ok(body);
    }
}
