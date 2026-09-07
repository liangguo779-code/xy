package com.campus.ai.retrieval;

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
 * Elasticsearch 向量存储门面：封装 ES Java Client 的 KNN 向量检索。
 *
 * <p>直接使用 ES Client 而非 LangChain4j 的 {@code ElasticsearchEmbeddingStore}，
 * 原因是我们需要自定义字段（source、chunk_index、section_title、section_path、
 * parent_index、is_parent）和自定义映射（dense_vector + cosine 相似度）。
 * LangChain4j 内置的 store 只支持扁平的"文本 + 向量"结构。
 *
 * <p>如果没有这个文件：
 * <ul>
 *   <li>知识文档的向量无法写入 Elasticsearch，检索功能完全不可用</li>
 *   <li>父子分块的元数据（parent_index、section_path）无法存储和查询</li>
 *   <li>知识库的增删改查（上传、重建、删除）将失去底层存储支撑</li>
 * </ul>
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
            if (exists) { log.info("ES index '{}' already exists", indexName()); return; }
            int dim = props.getVector().getEmbeddingDim();
            log.info("Creating ES index '{}' with dim={}", indexName(), dim);
            esClient.indices().create(c -> c.index(indexName()).mappings(m -> m
                    .properties("text", p -> p.text(t -> t))
                    .properties("source", p -> p.keyword(k -> k))
                    .properties("chunk_index", p -> p.integer(i -> i))
                    .properties("section_title", p -> p.text(t -> t))
                    .properties("section_path", p -> p.text(t -> t))
                    .properties("parent_index", p -> p.integer(i -> i))
                    .properties("is_parent", p -> p.boolean_(b -> b))
                    .properties("embedding", p -> p.denseVector(d -> d.dims(dim).index(true).similarity("cosine")))
            ));
        } catch (Exception e) { log.error("Ensure index failed: {}", e.getMessage(), e); }
    }

    private String indexName() { return props.getVector().getIndexName(); }
    private float[] embed(String text) { return embeddingModel.embed(text).content().vector(); }

    public void addAll(List<ChunkDto> chunks) {
        if (chunks == null || chunks.isEmpty()) return;
        try {
            int batchSize = 100, totalIndexed = 0;
            for (int i = 0; i < chunks.size(); i += batchSize) {
                List<ChunkDto> batch = chunks.subList(i, Math.min(i + batchSize, chunks.size()));
                BulkRequest.Builder br = new BulkRequest.Builder();
                for (ChunkDto c : batch) {
                    if (c.getContent() == null || c.getContent().isBlank()) continue;
                    // 用章节路径丰富文本，提升 Embedding 质量。
                    // 章节路径提供层次化上下文（如"第一章 / 第十条"），
                    // 帮助 Embedding 模型区分相似条款。
                    String enrichedText = c.getSectionPath() != null && !c.getSectionPath().isEmpty()
                            ? c.getSectionPath() + "\n" + c.getContent()
                            : c.getContent();
                    float[] vec = embed(enrichedText);
                    Map<String, Object> doc = new LinkedHashMap<>();
                    doc.put("text", c.getContent()); doc.put("source", c.getSource());
                    doc.put("chunk_index", c.getChunkIndex());
                    doc.put("section_title", c.getSectionTitle() == null ? "" : c.getSectionTitle());
                    doc.put("section_path", c.getSectionPath() == null ? "" : c.getSectionPath());
                    doc.put("parent_index", c.getParentIndex());
                    doc.put("is_parent", c.isParent());
                    doc.put("embedding", vec);
                    br.operations(op -> op.index(idx -> idx.index(indexName()).id(c.getSource() + "_" + c.getChunkIndex()).document(doc)));
                }
                BulkResponse resp = esClient.bulk(br.build());
                if (resp.errors()) resp.items().stream().filter(it -> it.error() != null).findFirst().ifPresent(it -> log.warn("Bulk error: {}", it.error().reason()));
                totalIndexed += (int) resp.items().stream().filter(it -> it.error() == null).count();
                Thread.sleep(50);
            }
            log.info("Vector store ingested {}/{} chunks", totalIndexed, chunks.size());
        } catch (Exception e) { log.error("addAll failed: {}", e.getMessage(), e); throw new RuntimeException(e); }
    }

    public void removeStaleBySource(String source, Set<String> newIds) {
        try {
            SearchRequest.Builder sb = new SearchRequest.Builder().index(indexName()).size(10000).query(q -> q.term(t -> t.field("source").value(source)));
            SearchResponse<Map> resp = esClient.search(sb.build(), Map.class);
            List<String> staleIds = new ArrayList<>();
            for (var h : resp.hits().hits()) { if (!newIds.contains(h.id())) staleIds.add(h.id()); }
            if (!staleIds.isEmpty()) {
                BulkRequest.Builder br = new BulkRequest.Builder();
                for (String id : staleIds) br.operations(op -> op.delete(d -> d.index(indexName()).id(id)));
                esClient.bulk(br.build());
            }
        } catch (Exception e) { log.warn("removeStale failed: {}", e.getMessage()); }
    }

    public void removeBySource(String source) {
        try { esClient.deleteByQuery(d -> d.index(indexName()).query(q -> q.term(t -> t.field("source").value(source)))); }
        catch (Exception e) { log.warn("removeBySource failed: {}", e.getMessage()); }
    }

    /**
     * 检索与查询匹配的分块。
     * 过滤掉父分块（is_parent=true）—— 只用子分块做检索。
     * 调用方（HybridRetriever.resolveParents）会将匹配到的子分块展开为父分块以提供完整上下文。
     */
    public List<Hit> search(String query, int topK, Set<String> disabledSources) {
        try {
            float[] qVec = embed(query);
            List<Float> qVecList = new ArrayList<>(qVec.length);
            for (float f : qVec) qVecList.add(f);
            // 多取一些候选，以补偿父分块过滤带来的损耗。
            int fetchSize = topK * 3;
            KnnQuery knn = new KnnQuery.Builder().field("embedding").k(fetchSize).numCandidates(fetchSize * 2).queryVector(qVecList).build();
            SearchRequest.Builder sb = new SearchRequest.Builder().index(indexName()).size(fetchSize).knn(knn);
            sb.source(s -> s.filter(f -> f.includes("text","source","chunk_index","section_title","section_path","parent_index","is_parent")));
            SearchResponse<Map> resp = esClient.search(sb.build(), Map.class);
            List<Hit> out = new ArrayList<>();
            for (co.elastic.clients.elasticsearch.core.search.Hit<Map> h : resp.hits().hits()) {
                Map src = h.source(); if (src == null) continue;
                int parentIndex = src.containsKey("parent_index") ? ((Number)src.get("parent_index")).intValue() : -1;
                boolean isParent = src.containsKey("is_parent") ? (Boolean)src.get("is_parent") : false;
                String sectionPath = src.containsKey("section_path") ? (String)src.get("section_path") : "";
                // 跳过父分块 —— 只用子分块做精确检索。
                if (isParent) continue;
                out.add(new Hit(
                        (String)src.get("source"),
                        src.containsKey("chunk_index") ? ((Number)src.get("chunk_index")).intValue() : 0,
                        (String)src.get("text"),
                        h.score(),
                        parentIndex,
                        isParent,
                        sectionPath
                ));
                if (out.size() >= topK) break;
            }
            if (disabledSources != null && !disabledSources.isEmpty()) out.removeIf(hit -> disabledSources.contains(hit.source()));
            return out;
        } catch (Exception e) { log.warn("search failed: {}", e.getMessage()); return List.of(); }
    }

    /**
     * 根据 source 和 chunkIndex 获取指定分块。
     * 用于在子分块匹配时获取其父分块。
     */
    public Hit getByIndex(String source, int chunkIndex) {
        try {
            String id = source + "_" + chunkIndex;
            GetResponse<Map> resp = esClient.get(g -> g.index(indexName()).id(id), Map.class);
            if (!resp.found()) return null;
            Map src = resp.source();
            if (src == null) return null;
            int parentIndex = src.containsKey("parent_index") ? ((Number)src.get("parent_index")).intValue() : -1;
            boolean isParent = src.containsKey("is_parent") ? (Boolean)src.get("is_parent") : false;
            String sectionPath = src.containsKey("section_path") ? (String)src.get("section_path") : "";
            return new Hit(
                    (String)src.get("source"),
                    src.containsKey("chunk_index") ? ((Number)src.get("chunk_index")).intValue() : 0,
                    (String)src.get("text"),
                    0.0,
                    parentIndex,
                    isParent,
                    sectionPath
            );
        } catch (Exception e) {
            log.warn("getByIndex failed: {}", e.getMessage());
            return null;
        }
    }

    public int count() { try { return (int) esClient.count(c -> c.index(indexName())).count(); } catch (Exception e) { return -1; } }

    public record Hit(String source, int chunkIndex, String content, double score, int parentIndex, boolean isParent, String sectionPath) {
        public Hit(String source, int chunkIndex, String content, double score, int parentIndex, boolean isParent) {
            this(source, chunkIndex, content, score, parentIndex, isParent, "");
        }
    }
}
