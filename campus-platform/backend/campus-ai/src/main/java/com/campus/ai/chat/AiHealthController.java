package com.campus.ai.chat;

import com.campus.ai.config.AiProperties;
import com.campus.ai.rag.retrieval.Bm25Index;
import com.campus.ai.rag.retrieval.VectorStoreFacade;
import com.campus.common.result.R;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Lightweight health endpoint that mirrors the previous Python service's {@code /health}.
 * Reports the configured LLM, vector store, embedder, and BM25 index size.
 */
@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AiHealthController {

    private final AiProperties props;
    private final VectorStoreFacade vectorStore;
    private final Bm25Index bm25;

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

        Map<String, Object> bm25c = new LinkedHashMap<>();
        bm25c.put("documentCount", bm25.size());
        checks.put("bm25", bm25c);

        Map<String, Object> knowledge = new LinkedHashMap<>();
        knowledge.put("dir", props.getKnowledge().getDir());
        checks.put("knowledge", knowledge);

        body.put("checks", checks);
        return R.ok(body);
    }
}
