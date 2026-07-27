package com.campus.ai.rag.retrieval;

import com.campus.ai.config.AiProperties;
import com.campus.ai.knowledge.model.ChunkDto;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.KnnQuery;
import co.elastic.clients.elasticsearch.core.*;
import co.elastic.clients.elasticsearch.core.search.Hit;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Vector store facade using the Elasticsearch Java Client directly.
 * Creates a dense_vector index, stores chunks with embeddings, and
 * searches using cosine similarity.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VectorStoreFacade {

    private final ElasticsearchClient esClient;
    private final dev.langchain4j.model.embedding.EmbeddingModel embeddingModel;
    private final AiProperties props;

    @PostConstruct
    void ensureIndex() {
        try {
            boolean exists = esClient.indices().exists(e -> e.index(indexName())).value();
            if (exists) {
                log.info("ES index '{}' already exists", indexName());
                return;
            }
            int dim = props.getVector().getEmbeddingDim();
            log.info("Creating ES index '{}' with dim={}...", indexName(), dim);
            esClient.indices().create(c -> c
                    .index(indexName())
                    .mappings(m -> m
                            .properties("text", p -> p.text(t -> t))
                            .properties("source", p -> p.keyword(k -> k))
                            .properties("chunk_index", p -> p.integer(i -> i))
                            .properties("section_title", p -> p.text(t -> t))
                            .properties("section_path", p -> p.text(t -> t))
                            .properties("embedding", p -> p.denseVector(d -> d
                                    .dims(dim)
                                    .index(true)
                                    .similarity("cosine")))
                    ));
            log.info("ES index '{}' created", indexName());
        } catch (Exception e) {
            log.error("Failed to ensure ES index '{}': {}", indexName(), e.getMessage(), e);
        }
    }

    private String indexName() {
        return props.getVector().getIndexName();
    }

    private float[] embed(String text) {
        return embeddingModel.embed(text).content().vector();
    }

    public void addAll(List<ChunkDto> chunks) {
        if (chunks == null || chunks.isEmpty()) return;
        try {
            BulkRequest.Builder br = new BulkRequest.Builder();
            int indexed = 0;
            for (ChunkDto c : chunks) {
                if (c.getContent() == null || c.getContent().isBlank()) {
                    log.warn("Skipping chunk with blank text: source={}, index={}", c.getSource(), c.getChunkIndex());
                    continue;
                }
                float[] vec = embed(c.getContent());
                Map<String, Object> doc = new LinkedHashMap<>();
                doc.put("text", c.getContent());
                doc.put("source", c.getSource());
                doc.put("chunk_index", c.getChunkIndex());
                doc.put("section_title", c.getSectionTitle() == null ? "" : c.getSectionTitle());
                doc.put("section_path", c.getSectionPath() == null ? "" : c.getSectionPath());
                doc.put("embedding", vec);
                br.operations(op -> op
                        .index(idx -> idx
                                .index(indexName())
                                .id(c.getSource() + "_" + c.getChunkIndex())
                                .document(doc)));
            }
            BulkResponse resp = esClient.bulk(br.build());
            if (resp.errors()) {
                resp.items().stream().filter(i -> i.error() != null).findFirst()
                        .ifPresent(i -> log.warn("First bulk error: {}: {}", i.id(), i.error().reason()));
            }
            indexed = (int) resp.items().stream().filter(i -> i.error() == null).count();
            log.info("Vector store ingested {}/{} chunks", indexed, chunks.size());
        } catch (Exception e) {
            log.error("Vector store addAll failed: {}", e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }

    public void removeBySource(String source) {
        try {
            esClient.deleteByQuery(d -> d
                    .index(indexName())
                    .query(q -> q.term(t -> t.field("source").value(source)))
            );
            log.info("Vector store removed all chunks for source={}", source);
        } catch (Exception e) {
            log.warn("Vector store removeBySource failed for {}: {}", source, e.getMessage());
        }
    }

    /**
     * KNN search using ES cosine similarity. Filters out disabled sources.
     */
    public List<Hit> search(String query, int topK, Set<String> disabledSources) {
        try {
            float[] qVec = embed(query);
            List<Float> qVecList = new ArrayList<>(qVec.length);
            for (float f : qVec) qVecList.add(f);

            // Build KnnQuery with k = topK
            KnnQuery knn = new KnnQuery.Builder()
                    .field("embedding")
                    .k(topK)
                    .numCandidates(topK * 2)
                    .queryVector(qVecList)
                    .build();

            SearchRequest.Builder sb = new SearchRequest.Builder()
                    .index(indexName())
                    .size(topK)
                    .knn(knn);
            sb.source(s -> s.filter(f -> f.includes(
                    "text", "source", "chunk_index", "section_title", "section_path")));

            SearchResponse<Map> resp = esClient.search(sb.build(), Map.class);
            List<Hit> out = new ArrayList<>();
            for (co.elastic.clients.elasticsearch.core.search.Hit<Map> h : resp.hits().hits()) {
                Map src = h.source();
                if (src == null) continue;
                out.add(new Hit(
                        (String) src.get("source"),
                        src.containsKey("chunk_index") ? ((Number) src.get("chunk_index")).intValue() : 0,
                        (String) src.get("text"),
                        h.score()
                ));
            }

            // Post-filter disabled sources (knn doesn't support must-not easily)
            if (disabledSources != null && !disabledSources.isEmpty()) {
                out.removeIf(hit -> disabledSources.contains(hit.source()));
            }
            return out;
        } catch (Exception e) {
            log.warn("Vector search failed: {}", e.getMessage());
            return List.of();
        }
    }

    public int count() {
        try {
            return (int) esClient.count(c -> c.index(indexName())).count();
        } catch (Exception e) {
            return -1;
        }
    }

    public record Hit(String source, int chunkIndex, String content, double score) {}
}
