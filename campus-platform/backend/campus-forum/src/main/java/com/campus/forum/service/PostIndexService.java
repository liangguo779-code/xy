package com.campus.forum.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ScriptSortType;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.mapping.KeywordProperty;
import co.elastic.clients.elasticsearch._types.mapping.Property;
import co.elastic.clients.elasticsearch._types.mapping.TextProperty;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.bulk.BulkOperation;
import co.elastic.clients.elasticsearch.indices.ExistsRequest;
import co.elastic.clients.json.JsonData;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.common.exception.BusinessException;
import com.campus.forum.document.PostDocument;
import com.campus.forum.entity.Post;
import com.campus.forum.mapper.PostMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 帖子搜索索引服务：维护帖子的 Elasticsearch 倒排索引（ik 中文分词）。
 *
 * <p>本类只负责"怎么写索引"，不负责"什么时候写"。调用方是 {@code PostEsSyncHandler}：
 * 业务侧改调 {@code EsOutboxService.recordIndex/recordDelete} 把同步意图写进事务性发件箱，
 * 由发件箱轮询/踢一脚落到此处，崩溃不丢同步意图。
 *
 * <p>ES 不可用时由调用方降级为数据库 LIKE 查询（见 ForumServiceImpl）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PostIndexService {

    private static final String INDEX_NAME = "posts";

    /** 深分页上限：ES from+size 受 max_result_window=10000 限制，超限直接报友好错误 */
    private static final int MAX_RESULT_WINDOW = 10000;

    /** 全量重建时每批入库条数 */
    private static final int REINDEX_BATCH_SIZE = 500;

    private final ElasticsearchClient esClient;
    private final PostMapper postMapper;

    @PostConstruct
    public void initIndex() {
        try {
            boolean exists = esClient.indices().exists(
                    ExistsRequest.of(e -> e.index(INDEX_NAME))).value();
            if (!exists) {
                createIndex();
                log.info("ES 索引 [{}] 创建成功", INDEX_NAME);
            }
        } catch (Exception e) {
            // 不能只 catch IOException：ES 返回 5xx/鉴权失败会抛 RuntimeException，
            // 若不兜底会令整个应用起不来（P1-14）。索引初始化失败降级为告警，检索路由自动降级 DB。
            log.warn("ES 索引初始化失败，ES 可能未启动，检索降级为数据库 LIKE: {}", e.getMessage());
        }
    }

    public void createIndex() throws IOException {
        Map<String, Property> properties = new HashMap<>();
        properties.put("title", Property.of(p -> p
                .text(TextProperty.of(t -> t
                        .analyzer("ik_max_word")
                        .searchAnalyzer("ik_smart")))));
        properties.put("content", Property.of(p -> p
                .text(TextProperty.of(t -> t
                        .analyzer("ik_max_word")
                        .searchAnalyzer("ik_smart")))));
        properties.put("category", Property.of(p -> p.keyword(KeywordProperty.of(k -> k))));
        properties.put("userId", Property.of(p -> p.long_(l -> l)));
        properties.put("isTop", Property.of(p -> p.integer(i -> i)));
        properties.put("viewCount", Property.of(p -> p.integer(i -> i)));
        properties.put("likeCount", Property.of(p -> p.integer(i -> i)));
        properties.put("commentCount", Property.of(p -> p.integer(i -> i)));
        properties.put("createTime", Property.of(p -> p.date(d -> d.format("strict_date_optional_time||epoch_millis"))));

        esClient.indices().create(c -> c
                .index(INDEX_NAME)
                .settings(s -> s
                        .numberOfShards("1")
                        .numberOfReplicas("0"))
                .mappings(m -> m.properties(properties)));
    }

    /**
     * 写入/覆盖帖子索引文档。失败<b>抛出</b>：调用方是 {@code PostEsSyncHandler}，
     * 由发件箱捕获后退避重试。业务代码请改调 {@code EsOutboxService.recordIndex}。
     */
    public void indexPost(Post post) {
        try {
            PostDocument doc = PostDocument.fromPost(post);
            esClient.index(i -> i
                    .index(INDEX_NAME)
                    .id(String.valueOf(post.getId()))
                    .document(doc));
        } catch (Exception e) {
            throw new RuntimeException("索引帖子失败, postId=" + post.getId() + ": " + e.getMessage(), e);
        }
    }

    /**
     * 从索引移除帖子文档。失败抛出，由发件箱退避重试（同 {@link #indexPost}）。
     * 文档不存在时 ES 返回 404，视作已达成目标，不抛出。
     */
    public void deletePost(Long postId) {
        try {
            esClient.delete(d -> d.index(INDEX_NAME).id(String.valueOf(postId)));
        } catch (co.elastic.clients.elasticsearch._types.ElasticsearchException e) {
            if (e.status() == 404) {
                return;
            }
            throw new RuntimeException("删除帖子索引失败, postId=" + postId + ": " + e.getMessage(), e);
        } catch (Exception e) {
            throw new RuntimeException("删除帖子索引失败, postId=" + postId + ": " + e.getMessage(), e);
        }
    }

    /** 全量重建索引（管理端触发）；成功后清空待修复队列（全量重建已覆盖全部差异） */
    public void reindexAll() {
        try {
            boolean exists = esClient.indices().exists(
                    ExistsRequest.of(e -> e.index(INDEX_NAME))).value();
            if (exists) {
                esClient.indices().delete(d -> d.index(INDEX_NAME));
            }
            createIndex();

            // id 游标分页读库（WHERE id > lastId ORDER BY id LIMIT n），
            // 避免一次性 selectList 全表进内存（P2-3）
            long total = 0;
            Long lastId = 0L;
            while (true) {
                List<Post> batch = postMapper.selectList(new LambdaQueryWrapper<Post>()
                        .eq(Post::getStatus, 1)
                        .gt(Post::getId, lastId)
                        .orderByAsc(Post::getId)
                        .last("LIMIT " + REINDEX_BATCH_SIZE));
                if (batch.isEmpty()) {
                    break;
                }
                List<BulkOperation> operations = new ArrayList<>();
                for (Post post : batch) {
                    PostDocument doc = PostDocument.fromPost(post);
                    operations.add(BulkOperation.of(op -> op
                            .index(idx -> idx
                                    .index(INDEX_NAME)
                                    .id(String.valueOf(post.getId()))
                                    .document(doc))));
                }
                esClient.bulk(BulkRequest.of(b -> b.operations(operations)));
                total += batch.size();
                lastId = batch.get(batch.size() - 1).getId();
                if (batch.size() < REINDEX_BATCH_SIZE) {
                    break;
                }
            }
            log.info("全量重建索引完成，共 {} 条帖子", total);
        } catch (Exception e) {
            log.error("全量重建帖子索引失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 倒排索引全文检索：title^2 + content 多字段匹配，支持分类/时间范围过滤、高亮片段与排序。
     *
     * <p>排序口径与 DB 路径（ForumServiceImpl.listPosts）保持一致（P1-13）：
     * <ul>
     *   <li>"hot"：script sort 复刻加权公式 like*5 + comment*3 + view，再按 isTop/createTime 倒序；</li>
     *   <li>"latest"：isTop 优先，再按 createTime 倒序；</li>
     *   <li>null：按相关度（_score）。</li>
     * </ul>
     *
     * @param keyword 检索关键词（必填）
     * @param category 分类过滤（可空）
     * @param sortBy 排序：null-相关度，"latest"-最新（置顶优先），"hot"-热度（加权公式）
     * @param startTime 创建时间下限（含，可空）
     * @param endTime 创建时间上限（不含，可空）
     * @param from 分页起始偏移
     * @param size 分页大小
     * @return 命中的帖子 ID 列表（按选定排序）、命中总数与高亮片段
     */
    public SearchResult search(String keyword, String category, String sortBy,
                               java.time.LocalDateTime startTime, java.time.LocalDateTime endTime,
                               int from, int size) throws IOException {
        // 深分页守卫（P2-3）：ES max_result_window 默认 10000，超限会 5xx，
        // 提前拦截并给出可操作的友好提示
        if (from + size > MAX_RESULT_WINDOW) {
            throw new BusinessException("搜索结果最多支持前 " + MAX_RESULT_WINDOW
                    + " 条，请增加筛选条件或调整时间范围缩小结果集");
        }
        boolean hot = "hot".equals(sortBy);
        boolean latest = "latest".equals(sortBy);
        var resp = esClient.search(s -> {
                    s.index(INDEX_NAME)
                            .from(from)
                            .size(size)
                            .query(q -> q
                                    .bool(b -> {
                                        b.must(m -> m
                                                .multiMatch(mm -> mm
                                                        .query(keyword)
                                                        .fields("title^2", "content")));
                                        if (category != null && !category.isEmpty()) {
                                            b.filter(f -> f
                                                    .term(t -> t
                                                            .field("category")
                                                            .value(category)));
                                        }
                                        if (startTime != null || endTime != null) {
                                            b.filter(f -> f.range(r -> {
                                                r.field("createTime");
                                                if (startTime != null) {
                                                    r.gte(JsonData.of(startTime
                                                            .atZone(java.time.ZoneId.systemDefault())
                                                            .toInstant().toEpochMilli()));
                                                }
                                                if (endTime != null) {
                                                    r.lt(JsonData.of(endTime
                                                            .atZone(java.time.ZoneId.systemDefault())
                                                            .toInstant().toEpochMilli()));
                                                }
                                                return r;
                                            }));
                                        }
                                        return b;
                                    }));
                    // 搜索高亮（P1-15）：与 campus-trade GoodsSearchService 同款用法，
                    // title 整段高亮，content 截取 100 字符片段，命中词用 <em> 包裹
                    s.highlight(h -> {
                        h.fields("title", hf -> hf.preTags("<em>").postTags("</em>"));
                        h.fields("content", hf -> hf.preTags("<em>").postTags("</em>").fragmentSize(100));
                        return h;
                    });
                    if (hot) {
                        // 热度排序复刻 DB 加权公式 like*5 + comment*3 + view（P1-13），
                        // 再以 isTop/createTime 兜底，保证同一参数两路结果序一致；
                        // size()==0 判空防止个别旧文档缺计数字段导致脚本报错
                        String script = "double like = doc['likeCount'].size() == 0 ? 0 : doc['likeCount'].value; "
                                + "double comment = doc['commentCount'].size() == 0 ? 0 : doc['commentCount'].value; "
                                + "double view = doc['viewCount'].size() == 0 ? 0 : doc['viewCount'].value; "
                                + "return like * 5 + comment * 3 + view;";
                        s.sort(so -> so.script(sc -> sc
                                        .type(ScriptSortType.Number)
                                        .order(SortOrder.Desc)
                                        .script(scp -> scp.inline(i -> i
                                                .lang("painless")
                                                .source(script)))))
                                .sort(so -> so.field(f -> f.field("isTop").order(SortOrder.Desc)))
                                .sort(so -> so.field(f -> f.field("createTime").order(SortOrder.Desc)));
                    } else if (latest) {
                        // 置顶优先，再按创建时间倒序（与 DB 路径 orderByDesc(isTop).orderByDesc(createTime) 一致）
                        s.sort(so -> so.field(f -> f.field("isTop").order(SortOrder.Desc)))
                                .sort(so -> so.field(f -> f.field("createTime").order(SortOrder.Desc)));
                    }
                    // sortBy 为空：不加 sort，默认按相关度（_score）
                    return s;
                },
                PostDocument.class);

        List<Long> ids = new ArrayList<>();
        Map<Long, Map<String, List<String>>> highlights = new HashMap<>();
        for (var hit : resp.hits().hits()) {
            try {
                long id = Long.parseLong(hit.id());
                ids.add(id);
                // 收集高亮片段（字段 -> 片段列表），回表后由调用方回填到 Post
                if (hit.highlight() != null && !hit.highlight().isEmpty()) {
                    highlights.put(id, hit.highlight());
                }
            } catch (NumberFormatException ignored) {
            }
        }
        long total = resp.hits().total() != null ? resp.hits().total().value() : ids.size();
        return new SearchResult(ids, total, highlights);
    }

    /** 检索结果：命中的帖子 ID（按所选排序）、命中总数与高亮片段（postId -> 字段 -> 片段列表） */
    public record SearchResult(List<Long> ids, long total,
                               Map<Long, Map<String, List<String>>> highlights) {}
}
