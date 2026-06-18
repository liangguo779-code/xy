package com.campus.trade.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.json.JsonData;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.trade.entity.Goods;
import com.campus.trade.mapper.GoodsMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class GoodsSearchService {

    private final ElasticsearchClient esClient;
    private final GoodsMapper goodsMapper;

    public Page<Goods> searchGoods(String keyword, Long categoryId, Integer type,
                                    BigDecimal minPrice, BigDecimal maxPrice,
                                    String condition, String sortBy,
                                    int page, int size) {
        try {
            List<Query> filters = new ArrayList<>();
            List<Query> mustNot = new ArrayList<>();

            // 精确过滤条件
            if (categoryId != null) {
                filters.add(Query.of(q -> q.term(t -> t
                        .field("categoryId").value(categoryId))));
            }
            if (type != null) {
                filters.add(Query.of(q -> q.term(t -> t
                        .field("type").value(type))));
            }
            if (condition != null && !condition.isEmpty()) {
                filters.add(Query.of(q -> q.term(t -> t
                        .field("condition").value(condition))));
            }

            // 只搜上架商品
            filters.add(Query.of(q -> q.term(t -> t
                    .field("status").value(0))));

            // 价格范围
            if (minPrice != null || maxPrice != null) {
                filters.add(Query.of(q -> q.range(r -> {
                    r.field("price");
                    if (minPrice != null) r.gte(JsonData.of(minPrice.doubleValue()));
                    if (maxPrice != null) r.lte(JsonData.of(maxPrice.doubleValue()));
                    return r;
                })));
            }

            // 全文检索：title 权重 3 倍
            Query matchQuery = Query.of(q -> q.multiMatch(m -> m
                    .fields("title^3", "description")
                    .query(keyword)
                    .type(co.elastic.clients.elasticsearch._types.query_dsl.TextQueryType.BestFields)
                    .fuzziness("AUTO")));

            // 组合查询
            Query boolQuery = Query.of(q -> q.bool(b -> b
                    .must(matchQuery)
                    .filter(filters)
                    .mustNot(mustNot)));

            // 排序
            String sortField = null;
            SortOrder sortOrder = SortOrder.Desc;
            if ("price_asc".equals(sortBy)) {
                sortField = "price";
                sortOrder = SortOrder.Asc;
            } else if ("price_desc".equals(sortBy)) {
                sortField = "price";
                sortOrder = SortOrder.Desc;
            } else if ("hottest".equals(sortBy)) {
                sortField = "viewCount";
                sortOrder = SortOrder.Desc;
            }

            // 构建搜索请求
            var searchBuilder = new co.elastic.clients.elasticsearch.core.SearchRequest.Builder()
                    .index("goods")
                    .query(boolQuery)
                    .from((page - 1) * size)
                    .size(size)
                    .highlight(h -> {
                        h.fields("title", hf -> hf.preTags("<em>").postTags("</em>"));
                        h.fields("description", hf -> hf.preTags("<em>").postTags("</em>").fragmentSize(100));
                        return h;
                    });

            if (sortField != null) {
                final String sf = sortField;
                final SortOrder so = sortOrder;
                searchBuilder.sort(s -> s.field(f -> f
                        .field(sf).order(so)));
            } else {
                // 默认按相关度排序
                searchBuilder.sort(s -> s.score(sc -> sc.order(SortOrder.Desc)));
            }

            SearchResponse<Void> response =
                    esClient.search(searchBuilder.build(), Void.class);

            // 提取商品 ID 列表，保持 ES 返回的顺序
            List<Long> goodsIds = new ArrayList<>();
            for (Hit<Void> hit : response.hits().hits()) {
                goodsIds.add(Long.parseLong(hit.id()));
            }

            if (goodsIds.isEmpty()) {
                Page<Goods> emptyPage = new Page<>(page, size, 0);
                emptyPage.setRecords(new ArrayList<>());
                return emptyPage;
            }

            // 批量查 MySQL 获取完整数据
            List<Goods> goodsList = goodsMapper.selectBatchIds(goodsIds);

            // 按 ES 返回的顺序重新排列
            Map<Long, Goods> goodsMap = goodsList.stream()
                    .collect(Collectors.toMap(Goods::getId, g -> g));

            List<Goods> orderedList = new ArrayList<>();
            Map<Long, Map<String, List<String>>> highlightMap = new java.util.HashMap<>();

            for (Hit<Void> hit : response.hits().hits()) {
                Long id = Long.parseLong(hit.id());
                Goods goods = goodsMap.get(id);
                if (goods != null) {
                    // 应用高亮
                    if (hit.highlight() != null) {
                        Map<String, List<String>> highlights = hit.highlight();
                        if (highlights.containsKey("title") && !highlights.get("title").isEmpty()) {
                            goods.setTitle(highlights.get("title").get(0));
                        }
                        if (highlights.containsKey("description") && !highlights.get("description").isEmpty()) {
                            goods.setDescription(highlights.get("description").get(0));
                        }
                    }
                    orderedList.add(goods);
                }
            }

            Page<Goods> result = new Page<>(page, size, response.hits().total() != null
                    ? response.hits().total().value() : 0);
            result.setRecords(orderedList);
            return result;

        } catch (Exception e) {
            log.error("ES 搜索失败: {}", e.getMessage(), e);
            throw new RuntimeException("ES 搜索失败", e);
        }
    }
}
