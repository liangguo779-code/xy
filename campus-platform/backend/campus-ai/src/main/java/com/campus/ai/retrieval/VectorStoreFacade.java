package com.campus.ai.retrieval;

import com.campus.ai.config.AiProperties;
import com.campus.ai.knowledge.model.ChunkDto;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.KnnQuery;
import co.elastic.clients.elasticsearch.core.*;
import co.elastic.clients.elasticsearch.core.search.Hit;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Elasticsearch 存储门面：封装 ES Java Client 的 KNN 向量检索与 IK 全文关键词检索。
 *
 * <p>直接使用 ES Client 而非 LangChain4j 的 {@code ElasticsearchEmbeddingStore}，
 * 原因是我们需要自定义字段（source、chunk_index、section_title、section_path、
 * parent_index、is_parent）和自定义映射（dense_vector + cosine 相似度 +
 * text 字段的 ik_max_word/ik_smart 中文分析器）。
 * LangChain4j 内置的 store 只支持扁平的"文本 + 向量"结构。
 *
 * <p>如果没有这个文件：
 * <ul>
 *   <li>知识文档的向量无法写入 Elasticsearch，检索功能完全不可用</li>
 *   <li>父子分块的元数据（parent_index、section_path）无法存储和查询</li>
 *   <li>知识库的增删改查（上传、重建、删除）将失去底层存储支撑</li>
 *   <li>关键词检索（IK 分词 BM25）通道不可用，只剩纯向量检索</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VectorStoreFacade {

    /** IK 分词器：建索引用细粒度切分提高召回，检索用智能切分避免过度匹配。 */
    private static final String INDEX_ANALYZER = "ik_max_word";
    private static final String SEARCH_ANALYZER = "ik_smart";

    private final ElasticsearchClient esClient;
    private final dev.langchain4j.model.embedding.EmbeddingModel embeddingModel;
    private final AiProperties props;

    /**
     * 检测到旧映射（text 无 IK 分析器）并已删除重建索引时置 true。
     * 旧数据随索引删除丢失，需由 KnowledgeService 启动后触发全量重建回填。
     */
    private final AtomicBoolean pendingRebuild = new AtomicBoolean(false);

    /** 启动时确保 ES 索引存在且映射含 IK 分析器；缺失或过时则重建索引。 */
    @PostConstruct
    void ensureIndex() {
        try {
            boolean exists = esClient.indices().exists(e -> e.index(indexName())).value();
            if (exists) {
                if (ikMappingReady()) {
                    log.info("ES index '{}' already exists with IK analyzers", indexName());
                    return;
                }
                // 映射里的 analyzer 不可原地修改 —— 旧索引（无 IK）只能删掉重建。
                // 语料都在磁盘知识文件里，重建后由调用方全量重新入库即可恢复。
                log.warn("ES index '{}' exists but text field lacks IK analyzers, recreating index",
                        indexName());
                esClient.indices().delete(d -> d.index(indexName()));
                pendingRebuild.set(true);
            }
            int dim = props.getVector().getEmbeddingDim();
            log.info("Creating ES index '{}' with dim={}, analyzers={}/{}",
                    indexName(), dim, INDEX_ANALYZER, SEARCH_ANALYZER);
            esClient.indices().create(c -> c.index(indexName()).mappings(m -> m
                    .properties("text", p -> p.text(t -> t
                            .analyzer(INDEX_ANALYZER)
                            .searchAnalyzer(SEARCH_ANALYZER)))
                    .properties("source", p -> p.keyword(k -> k))
                    .properties("chunk_index", p -> p.integer(i -> i))
                    .properties("section_title", p -> p.text(t -> t
                            .analyzer(INDEX_ANALYZER)
                            .searchAnalyzer(SEARCH_ANALYZER)))
                    .properties("section_path", p -> p.text(t -> t
                            .analyzer(INDEX_ANALYZER)
                            .searchAnalyzer(SEARCH_ANALYZER)))
                    .properties("parent_index", p -> p.integer(i -> i))
                    .properties("is_parent", p -> p.boolean_(b -> b))
                    .properties("embedding", p -> p.denseVector(d -> d.dims(dim).index(true).similarity("cosine")))
            ));
        } catch (Exception e) { log.error("Ensure index failed: {}", e.getMessage(), e); }
    }

    /** 索引存在但因旧映射被删除重建（数据已清空），调用方需触发知识库全量重建回填。 */
    public boolean isPendingRebuild() {
        return pendingRebuild.get();
    }

    /** 读取 text 字段映射，判断是否已配置 IK 分析器。 */
    private boolean ikMappingReady() {
        try {
            var resp = esClient.indices().getMapping(g -> g.index(indexName()));
            var mapping = resp.result().get(indexName());
            if (mapping == null || mapping.mappings() == null) return false;
            var textProp = mapping.mappings().properties().get("text");
            return textProp != null && textProp.isText()
                    && INDEX_ANALYZER.equals(textProp.text().analyzer());
        } catch (Exception e) {
            log.warn("Failed to read mapping of '{}', will recreate: {}", indexName(), e.getMessage());
            return false;
        }
    }

    private String indexName() { return props.getVector().getIndexName(); }
    private float[] embed(String text) { return embeddingModel.embed(text).content().vector(); }

    /** 批量写入分块（每批 100 条，批间歇 50ms 削峰）；文档 ID 为 source_chunkIndex，天然幂等覆盖。 */
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

    /** 删除指定来源下不在 newIds 中的旧文档（rebuild 时清理切分变更后残留的分块）。 */
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

    /** 按来源删除全部分块。 */
    public void removeBySource(String source) {
        try { esClient.deleteByQuery(d -> d.index(indexName()).query(q -> q.term(t -> t.field("source").value(source)))); }
        catch (Exception e) { log.warn("removeBySource failed: {}", e.getMessage()); }
    }

    /** 索引中出现过的全部 source（去重），供重建时发现磁盘已删除的孤儿来源。 */
    public Set<String> listSources() {
        try {
            SearchResponse<Map> resp = esClient.search(s -> s
                    .index(indexName())
                    .size(0)
                    .aggregations("srcs", a -> a.terms(t -> t.field("source").size(10000))),
                    Map.class);
            Set<String> out = new HashSet<>();
            for (var b : resp.aggregations().get("srcs").sterms().buckets().array()) {
                out.add(b.key().stringValue());
            }
            return out;
        } catch (Exception e) {
            log.warn("listSources failed: {}", e.getMessage());
            return Set.of();
        }
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
                String source = (String)src.get("source");
                // 先过滤禁用来源，再做 topK 截断：
                // 旧顺序（先截断后过滤）会把名额浪费在禁用文件上，导致结果数不足 topK。
                if (disabledSources != null && !disabledSources.isEmpty() && disabledSources.contains(source)) continue;
                out.add(new Hit(
                        source,
                        src.containsKey("chunk_index") ? ((Number)src.get("chunk_index")).intValue() : 0,
                        (String)src.get("text"),
                        h.score(),
                        parentIndex,
                        isParent,
                        sectionPath
                ));
                if (out.size() >= topK) break;
            }
            return out;
        } catch (Exception e) { log.warn("search failed: {}", e.getMessage()); return List.of(); }
    }

    /**
     * IK 全文关键词检索（替代原内存 jieba BM25 索引）。
     *
     * <p>text 字段建索引时用 ik_max_word 细粒度切分、检索时用 ik_smart 智能切分，
     * ES 按 BM25 公式打分。查询走 multi_match（text 为主，章节标题/路径加权），
     * 与向量通道同样的父分块过滤与禁用来源过滤。
     *
     * @param query            查询文本（ES 内部做 IK 分词）
     * @param topK             返回条数上限
     * @param disabledSources  禁用的知识来源，命中则跳过（可为 null）
     */
    public List<KeywordHit> searchKeyword(String query, int topK, Set<String> disabledSources) {
        if (query == null || query.isBlank() || topK <= 0) return List.of();
        try {
            // 多取候选，补偿禁用来源过滤带来的损耗。
            int fetchSize = topK * 3;
            SearchRequest.Builder sb = new SearchRequest.Builder().index(indexName()).size(fetchSize)
                    .query(q -> q.bool(b -> {
                        b.must(m -> m.multiMatch(mm -> mm
                                .query(query)
                                .fields("text^2", "section_title", "section_path")));
                        // 只用子分块做检索（父分块由 HybridRetriever 在命中后展开）。
                        b.filter(f -> f.term(t -> t.field("is_parent").value(false)));
                        if (disabledSources != null && !disabledSources.isEmpty()) {
                            List<FieldValue> vals = disabledSources.stream().map(FieldValue::of).toList();
                            b.mustNot(mn -> mn.terms(t -> t.field("source").terms(tv -> tv.value(vals))));
                        }
                        return b;
                    }));
            sb.source(s -> s.filter(f -> f.includes("text", "source", "chunk_index")));
            SearchResponse<Map> resp = esClient.search(sb.build(), Map.class);
            List<KeywordHit> out = new ArrayList<>();
            for (var h : resp.hits().hits()) {
                Map src = h.source();
                if (src == null) continue;
                out.add(new KeywordHit(
                        (String) src.get("source"),
                        src.containsKey("chunk_index") ? ((Number) src.get("chunk_index")).intValue() : 0,
                        (String) src.get("text"),
                        h.score() == null ? 0d : h.score()
                ));
                if (out.size() >= topK) break;
            }
            return out;
        } catch (Exception e) {
            log.warn("searchKeyword failed: {}", e.getMessage());
            return List.of();
        }
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

    /** 索引中的文档总数，查询失败返回 -1。 */
    public int count() { try { return (int) esClient.count(c -> c.index(indexName())).count(); } catch (Exception e) { return -1; } }

    /**
     * 向量库中的分块命中。
     *
     * @param parentIndex 父分块下标，-1 表示无父分块
     */
    public record Hit(String source, int chunkIndex, String content, double score, int parentIndex, boolean isParent, String sectionPath) {
        /** 无章节路径的便捷构造器。 */
        public Hit(String source, int chunkIndex, String content, double score, int parentIndex, boolean isParent) {
            this(source, chunkIndex, content, score, parentIndex, isParent, "");
        }
    }

    /** 关键词检索（IK 全文）命中结果。 */
    public record KeywordHit(String source, int chunkIndex, String content, double score) {}
}
