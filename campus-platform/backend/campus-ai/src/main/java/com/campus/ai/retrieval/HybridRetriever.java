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

    private final VectorStoreFacade vectorStore;
    private final Bm25Index bm25;
    private final DisabledFilesCache disabled;
    private final AiProperties props;

    public List<Hit> searchMulti(List<String> queries, int topK) {
        if (queries == null || queries.isEmpty()) return List.of();
        Set<String> disabledSet = disabled.getDisabled();

        // 标准混合检索：向量 + BM25，通过 RRF 融合
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

        // 解析父子关系：将子分块替换为其父分块（完整文章）
        result = resolveParents(result);

        return result;
    }

    public List<Hit> search(String query, int topK) {
        return searchMulti(List.of(query), topK);
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

    public record Hit(String source, int chunkIndex, String content, double score, String sectionPath) {
        public Hit(String source, int chunkIndex, String content, double score) {
            this(source, chunkIndex, content, score, "");
        }
    }
}
