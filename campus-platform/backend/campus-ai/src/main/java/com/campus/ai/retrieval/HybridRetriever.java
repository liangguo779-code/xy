package com.campus.ai.retrieval;

import com.campus.ai.config.AiProperties;
import com.campus.ai.knowledge.store.DisabledFilesCache;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.*;

/**
 * 混合检索器：向量检索 + BM25 关键词检索，通过倒数排名融合（RRF, k=60）合并结果。
 *
 * <p>向量通道只对第一个查询执行一次；BM25 通道对所有改写后的查询并行检索。
 * 结果去重后返回一个扁平列表。
 *
 * <p>父子分块解析：当匹配到子分块时，自动替换为父分块（完整文章），
 * 为 LLM 生成提供完整的上下文。
 *
 * <p>如果没有这个文件：
 * <ul>
 *   <li>只剩纯向量检索，中文关键词精确匹配（如"休学""奖学金"）的召回率会大幅下降</li>
 *   <li>只剩纯 BM25，语义相近但用词不同的问题（如"请假"vs"缺席"）无法召回</li>
 *   <li>子分块匹配后不会展开为父分块，LLM 拿到的上下文不完整</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HybridRetriever {

    /** BM25 多路查询上限（含原查询与同义变体），限制并行检索成本。 */
    private static final int MAX_BM25_QUERIES = 8;

    private final VectorStoreFacade vectorStore;
    private final Bm25Index bm25;
    /** 禁用来源缓存，检索前取出一次性过滤，避免逐条查询开销。 */
    private final DisabledFilesCache disabled;
    private final AiProperties props;
    /** 规则同义扩展器：为 BM25 通道补充口语→正式用词的变体查询。 */
    private final SynonymExpander synonymExpander;

    /**
     * 对多个改写查询执行混合检索（向量仅用首查询，BM25 并行扫全部查询），RRF 融合后
     * 再做关键词保底补齐与父子分块展开。
     *
     * @param queries 改写后的查询词列表
     * @param topK    最终返回条数上限
     */
    public List<Hit> searchMulti(List<String> queries, int topK) {
        if (queries == null || queries.isEmpty()) return List.of();
        Set<String> disabledSet = disabled.getDisabled();

        // 标准混合检索：向量 + BM25，通过 RRF 融合
        List<VectorStoreFacade.Hit> vec = vectorStore.search(queries.get(0), topK * 2 + disabledSet.size() * 5, disabledSet);
        // BM25 通道：对每个查询做规则同义扩展（如 挂科→不及格/补考/重学），
        // 多路并行检索提升词面召回；变体总量封顶，避免查询爆炸拖慢融合。
        List<String> bm25Queries = expandForBm25(queries);
        List<List<Bm25Index.Hit>> bm25PerQuery = new ArrayList<>();
        ExecutorService exec = Executors.newFixedThreadPool(Math.min(bm25Queries.size(), 5));
        try {
            List<Future<List<Bm25Index.Hit>>> futures = new ArrayList<>();
            for (String q : bm25Queries) {
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
        // 调试：打印 top 结果用于诊断
        if (!bm25PerQuery.isEmpty() && !bm25PerQuery.get(0).isEmpty()) {
            log.info("BM25 top-3 for '{}': {}", queries.get(0),
                    bm25PerQuery.get(0).stream().limit(3)
                            .map(h -> "idx=" + h.chunkIndex() + " sc=" + String.format("%.2f", h.score()))
                            .toList());
        }
        log.info("Fused top-5: {}", fused.stream().limit(5)
                .map(h -> "idx=" + h.chunkIndex() + " sc=" + String.format("%.4f", h.score()))
                .toList());

        // 保底：确保关键词精确匹配的 BM25 结果一定出现在最终结果中。
        // 从第一个查询中提取内容关键词（去除停用词），直接执行 BM25 检索。
        // 作为向量检索遗漏时的安全网。
        List<Hit> result = new ArrayList<>(fused);
        Set<String> seen = new HashSet<>();
        for (Hit h : fused) seen.add(h.source() + "_" + h.chunkIndex());
        try {
            String contentQuery = queries.get(0)
                    .replaceAll("怎么办理|办理|如何|怎样|流程|请问|申请|需要|是否", " ")
                    .replaceAll("\\s+", " ").trim();
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

        // 跨文档同内容去重：条文在政策原文与办事指南摘录中重复出现时，
        // 只保留排名最高的一条，避免同一次回答的参考来源里出现整段重复。
        Set<String> seenContent = new HashSet<>();
        List<Hit> deduped = new ArrayList<>();
        for (Hit h : result) {
            if (seenContent.add(normalizeForDedup(h.content()))) {
                deduped.add(h);
            }
        }
        result = deduped;

        // 解析父子关系：将子分块替换为其父分块（完整文章）
        result = resolveParents(result);

        return result;
    }

    /** 去重归一：去掉【章节路径】头与所有空白，使同文不同路径头的重复条文可识别。 */
    private static String normalizeForDedup(String content) {
        if (content == null) return "";
        return content.replaceAll("【[^】]*】", "").replaceAll("\\s+", "");
    }

    /** 单查询便捷入口，等价于 searchMulti(List.of(query), topK)。 */
    public List<Hit> search(String query, int topK) {
        return searchMulti(List.of(query), topK);
    }

    /**
     * 为 BM25 通道展开查询：每个原查询保留自身，追加规则同义变体。
     * 全局去重并封顶（最多 {@value #MAX_BM25_QUERIES} 路），防止多查询 × 多变体爆炸。
     */
    private List<String> expandForBm25(List<String> queries) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        for (String q : queries) {
            for (String v : synonymExpander.expand(q)) {
                out.add(v);
                if (out.size() >= MAX_BM25_QUERIES) return new ArrayList<>(out);
            }
        }
        return new ArrayList<>(out);
    }

    /**
     * 解析父子关系：当匹配到子分块时，替换为父分块（完整文章）以提供完整上下文。
     */
    private List<Hit> resolveParents(List<Hit> hits) {
        List<Hit> resolved = new ArrayList<>();
        Set<String> seenParents = new HashSet<>();

        for (Hit hit : hits) {
            // 从向量存储获取分块，检查是否存在父子关系
            VectorStoreFacade.Hit vecHit = vectorStore.getByIndex(hit.source(), hit.chunkIndex());

            if (vecHit != null && vecHit.parentIndex() >= 0) {
                // 这是子分块，获取其父分块
                String parentKey = hit.source() + "_" + vecHit.parentIndex();
                if (!seenParents.contains(parentKey)) {
                    VectorStoreFacade.Hit parentHit = vectorStore.getByIndex(hit.source(), vecHit.parentIndex());
                    if (parentHit != null) {
                        resolved.add(new Hit(
                                parentHit.source(),
                                parentHit.chunkIndex(),
                                parentHit.content(),
                                hit.score(),  // 保留子分块的相关性分数
                                parentHit.sectionPath()
                        ));
                        seenParents.add(parentKey);
                    }
                }
                // 跳过子分块本身
            } else {
                // 已经是父分块或独立分块
                String key = hit.source() + "_" + hit.chunkIndex();
                if (!seenParents.contains(key)) {
                    resolved.add(hit);
                    seenParents.add(key);
                }
            }
        }

        return resolved;
    }

    /** RRF 融合：每个命中按 1/(k+rank+1) 累加得分（k 为排名常量，默认 60），同文档多路命中分数叠加。 */
    private List<Hit> fuse(List<VectorStoreFacade.Hit> vec, List<List<Bm25Index.Hit>> bm25Lists, int k) {
        Map<String, Double> scores = new HashMap<>();
        Map<String, Hit> docs = new HashMap<>();
        // 向量检索：标准 RRF 权重
        for (int rank = 0; rank < vec.size(); rank++) {
            VectorStoreFacade.Hit h = vec.get(rank);
            String key = key(h.source(), h.chunkIndex());
            scores.merge(key, 1d / (k + rank + 1), Double::sum);
            docs.putIfAbsent(key, new Hit(h.source(), h.chunkIndex(), h.content(), h.score(), h.sectionPath()));
        }
        // BM25：加权提升（4.0 倍），因为中文关键词匹配对政策文档非常精确，
        // 而当前 Embedding 模型（BGE small-zh）的语义排序能力有限。
        // 不加权的话，BM25 的正确结果会被向量检索的噪声结果淹没。
        double bm25Boost = 4.0d;
        for (List<Bm25Index.Hit> list : bm25Lists) {
            for (int rank = 0; rank < list.size(); rank++) {
                Bm25Index.Hit h = list.get(rank);
                String key = key(h.source(), h.chunkIndex());
                double boost = bm25Boost;
                // BM25 第一名额外加权：确保精确关键词匹配能进入 top 5。
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

    /**
     * 融合后的命中结果。
     *
     * @param sectionPath 分块所属章节路径（如"第三章 > 第七条"），展示优先于 source
     */
    public record Hit(String source, int chunkIndex, String content, double score, String sectionPath) {
        /** 兼容无章节路径场景的便捷构造器。 */
        public Hit(String source, int chunkIndex, String content, double score) {
            this(source, chunkIndex, content, score, "");
        }
    }
}
