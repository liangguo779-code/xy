package com.campus.ai.rag.retrieval;

import com.campus.ai.config.AiProperties;
import com.campus.ai.knowledge.store.DisabledFilesCache;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.*;

/**
 * Hybrid retriever ported from the Python service's
 * {@code rag.retriever.hybrid_search_multi_query}. The vector pass runs once on the first
 * query; BM25 fans out across all rewritten queries; results are merged with reciprocal
 * rank fusion (RRF, k=60) and returned as a flat, de-duplicated list.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HybridRetriever {

    private final VectorStoreFacade vectorStore;
    private final Bm25Index bm25;
    private final DisabledFilesCache disabled;
    private final AiProperties props;

    public List<Hit> searchMulti(List<String> queries, int topK) {
        if (queries == null || queries.isEmpty()) return List.of();
        Set<String> disabledSet = disabled.getDisabled();

        List<VectorStoreFacade.Hit> vec = vectorStore.search(queries.get(0), topK * 2 + disabledSet.size() * 5, disabledSet);
        List<List<Bm25Index.Hit>> bm25PerQuery = new ArrayList<>();
        ExecutorService exec = Executors.newFixedThreadPool(Math.min(queries.size(), 5));
        try {
            List<Future<List<Bm25Index.Hit>>> futures = new ArrayList<>();
            for (String q : queries) {
                futures.add(exec.submit(() -> bm25.search(q, topK * 2, disabledSet)));
            }
            for (Future<List<Bm25Index.Hit>> f : futures) {
                try {
                    bm25PerQuery.add(f.get());
                } catch (Exception e) {
                    log.warn("BM25 search failed: {}", e.getMessage());
                }
            }
        } finally {
            exec.shutdown();
        }
        return fuse(vec, bm25PerQuery, props.getKnowledge().getBm25K());
    }

    public List<Hit> search(String query, int topK) {
        return searchMulti(List.of(query), topK);
    }

    private List<Hit> fuse(List<VectorStoreFacade.Hit> vec, List<List<Bm25Index.Hit>> bm25Lists, int k) {
        Map<String, Double> scores = new HashMap<>();
        Map<String, Hit> docs = new HashMap<>();
        for (int rank = 0; rank < vec.size(); rank++) {
            VectorStoreFacade.Hit h = vec.get(rank);
            String key = key(h.source(), h.chunkIndex());
            scores.merge(key, 1d / (k + rank + 1), Double::sum);
            docs.putIfAbsent(key, new Hit(h.source(), h.chunkIndex(), h.content(), h.score()));
        }
        for (List<Bm25Index.Hit> list : bm25Lists) {
            for (int rank = 0; rank < list.size(); rank++) {
                Bm25Index.Hit h = list.get(rank);
                String key = key(h.source(), h.chunkIndex());
                scores.merge(key, 1d / (k + rank + 1), Double::sum);
                docs.putIfAbsent(key, new Hit(h.source(), h.chunkIndex(), h.content(), h.score()));
            }
        }
        return scores.entrySet().stream()
                .sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
                .map(e -> docs.get(e.getKey()))
                .toList();
    }

    private static String key(String source, int chunkIndex) {
        return source + "_" + chunkIndex;
    }

    public record Hit(String source, int chunkIndex, String content, double score) {}
}
