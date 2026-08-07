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
 *
 * <p>Parent-child chunk resolution: when a child chunk is matched, the parent chunk
 * (full article) is returned instead, providing complete context for generation.
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

        // Standard hybrid search: vector + BM25 via RRF
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
        List<Hit> fused = fuse(vec, bm25PerQuery, props.getKnowledge().getBm25K());
        // Debug: log top results for diagnosis
        if (!bm25PerQuery.isEmpty() && !bm25PerQuery.get(0).isEmpty()) {
            log.info("BM25 top-3 for '{}': {}", queries.get(0),
                    bm25PerQuery.get(0).stream().limit(3)
                            .map(h -> "idx=" + h.chunkIndex() + " sc=" + String.format("%.2f", h.score()))
                            .toList());
        }
        log.info("Fused top-5: {}", fused.stream().limit(5)
                .map(h -> "idx=" + h.chunkIndex() + " sc=" + String.format("%.4f", h.score()))
                .toList());

        // Guarantee: keyword-exact BM25 matches appear in the results.
        // Extracts content keywords from the first query (stripping stop-words) and
        // runs a direct BM25 search. Safety net for when embedding ranking misses.
        List<Hit> result = new ArrayList<>(fused);
        Set<String> seen = new HashSet<>();
        for (Hit h : fused) seen.add(h.source() + "_" + h.chunkIndex());
        try {
            String contentQuery = queries.get(0)
                    .replaceAll("怎么办理|办理|如何|怎样|流程|请问|申请|需要|是否", " ")
                    .replaceAll("\s+", " ").trim();
            if (!contentQuery.isEmpty()) {
                List<Bm25Index.Hit> direct = bm25.search(contentQuery, topK, disabledSet);
                for (Bm25Index.Hit h : direct) {
                    String key = h.source() + "_" + h.chunkIndex();
                    if (!seen.add(key)) continue;
                    result.add(0, new Hit(h.source(), h.chunkIndex(), h.content(), h.score()));
                }
            }
        } catch (Exception e) {
            log.warn("Direct BM25 fallback failed: {}", e.getMessage());
        }

        if (result.size() > topK) result = result.subList(0, topK);

        // Resolve parent-child: replace child chunks with their parent (full article)
        result = resolveParents(result);

        return result;
    }

    public List<Hit> search(String query, int topK) {
        return searchMulti(List.of(query), topK);
    }

    /**
     * Resolve parent-child relationships: when a child chunk is matched,
     * replace it with the parent chunk (full article) for complete context.
     */
    private List<Hit> resolveParents(List<Hit> hits) {
        List<Hit> resolved = new ArrayList<>();
        Set<String> seenParents = new HashSet<>();

        for (Hit hit : hits) {
            // Try to get the chunk from vector store to check parent relationship
            VectorStoreFacade.Hit vecHit = vectorStore.getByIndex(hit.source(), hit.chunkIndex());

            if (vecHit != null && vecHit.parentIndex() >= 0) {
                // This is a child chunk, fetch the parent
                String parentKey = hit.source() + "_" + vecHit.parentIndex();
                if (!seenParents.contains(parentKey)) {
                    VectorStoreFacade.Hit parentHit = vectorStore.getByIndex(hit.source(), vecHit.parentIndex());
                    if (parentHit != null) {
                        resolved.add(new Hit(
                                parentHit.source(),
                                parentHit.chunkIndex(),
                                parentHit.content(),
                                hit.score(),  // Keep the child's relevance score
                                parentHit.sectionPath()
                        ));
                        seenParents.add(parentKey);
                    }
                }
                // Skip the child chunk itself
            } else {
                // This is already a parent chunk or standalone chunk
                String key = hit.source() + "_" + hit.chunkIndex();
                if (!seenParents.contains(key)) {
                    resolved.add(hit);
                    seenParents.add(key);
                }
            }
        }

        return resolved;
    }

    private List<Hit> fuse(List<VectorStoreFacade.Hit> vec, List<List<Bm25Index.Hit>> bm25Lists, int k) {
        Map<String, Double> scores = new HashMap<>();
        Map<String, Hit> docs = new HashMap<>();
        // Vector search: standard RRF weight
        for (int rank = 0; rank < vec.size(); rank++) {
            VectorStoreFacade.Hit h = vec.get(rank);
            String key = key(h.source(), h.chunkIndex());
            scores.merge(key, 1d / (k + rank + 1), Double::sum);
            docs.putIfAbsent(key, new Hit(h.source(), h.chunkIndex(), h.content(), h.score(), h.sectionPath()));
        }
        // BM25: boosted weight (4.0x) because Chinese keyword matching is highly
        // precise for policy documents, and the current embedding model (BGE small-zh)
        // is not strong enough for semantic ranking. Without this boost, BM25's
        // correct results get drowned by the vector search's noisy results.
        double bm25Boost = 4.0d;
        for (List<Bm25Index.Hit> list : bm25Lists) {
            for (int rank = 0; rank < list.size(); rank++) {
                Bm25Index.Hit h = list.get(rank);
                String key = key(h.source(), h.chunkIndex());
                double boost = bm25Boost;
                // Extra boost for BM25 top-1: ensures exact keyword matches enter top 5.
                if (rank == 0) boost += 2.0d;
                scores.merge(key, boost / (k + rank + 1), Double::sum);
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

    public record Hit(String source, int chunkIndex, String content, double score, String sectionPath) {
        public Hit(String source, int chunkIndex, String content, double score) {
            this(source, chunkIndex, content, score, "");
        }
    }
}
