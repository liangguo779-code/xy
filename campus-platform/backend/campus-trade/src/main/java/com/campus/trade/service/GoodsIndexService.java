package com.campus.trade.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.mapping.Property;
import co.elastic.clients.elasticsearch._types.mapping.TextProperty;
import co.elastic.clients.elasticsearch._types.mapping.KeywordProperty;
import co.elastic.clients.elasticsearch._types.mapping.DoubleNumberProperty;
import co.elastic.clients.elasticsearch._types.mapping.LongNumberProperty;
import co.elastic.clients.elasticsearch._types.mapping.IntegerNumberProperty;
import co.elastic.clients.elasticsearch._types.mapping.DateProperty;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.bulk.BulkOperation;
import co.elastic.clients.elasticsearch.indices.ExistsRequest;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.trade.document.GoodsDocument;
import com.campus.trade.entity.Goods;
import com.campus.trade.mapper.GoodsMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class GoodsIndexService {

    private static final String INDEX_NAME = "goods";

    private final ElasticsearchClient esClient;
    private final GoodsMapper goodsMapper;

    @PostConstruct
    public void initIndex() {
        try {
            boolean exists = esClient.indices().exists(
                    ExistsRequest.of(e -> e.index(INDEX_NAME))).value();
            if (!exists) {
                createIndex();
                log.info("ES 索引 [{}] 创建成功", INDEX_NAME);
            }
        } catch (IOException e) {
            log.warn("ES 索引初始化失败，ES 可能未启动: {}", e.getMessage());
        }
    }

    public void createIndex() throws IOException {
        Map<String, Property> properties = new HashMap<>();

        properties.put("title", Property.of(p -> p
                .text(TextProperty.of(t -> t
                        .analyzer("ik_max_word")
                        .searchAnalyzer("ik_smart")))));

        properties.put("description", Property.of(p -> p
                .text(TextProperty.of(t -> t
                        .analyzer("ik_max_word")
                        .searchAnalyzer("ik_smart")))));

        properties.put("price", Property.of(p -> p.double_(DoubleNumberProperty.of(d -> d))));
        properties.put("originalPrice", Property.of(p -> p.double_(DoubleNumberProperty.of(d -> d))));
        properties.put("categoryId", Property.of(p -> p.long_(LongNumberProperty.of(l -> l))));
        properties.put("category", Property.of(p -> p.keyword(KeywordProperty.of(k -> k))));
        properties.put("condition", Property.of(p -> p.keyword(KeywordProperty.of(k -> k))));
        properties.put("location", Property.of(p -> p.keyword(KeywordProperty.of(k -> k))));
        properties.put("type", Property.of(p -> p.integer(IntegerNumberProperty.of(i -> i))));
        properties.put("status", Property.of(p -> p.integer(IntegerNumberProperty.of(i -> i))));
        properties.put("images", Property.of(p -> p.keyword(KeywordProperty.of(k -> k))));
        properties.put("userId", Property.of(p -> p.long_(LongNumberProperty.of(l -> l))));
        properties.put("viewCount", Property.of(p -> p.integer(IntegerNumberProperty.of(i -> i))));
        properties.put("wantCount", Property.of(p -> p.integer(IntegerNumberProperty.of(i -> i))));
        properties.put("likeCount", Property.of(p -> p.integer(IntegerNumberProperty.of(i -> i))));
        properties.put("refreshTime", Property.of(p -> p.date(DateProperty.of(d -> d.format("strict_date_optional_time||epoch_millis")))));
        properties.put("createTime", Property.of(p -> p.date(DateProperty.of(d -> d.format("strict_date_optional_time||epoch_millis")))));

        esClient.indices().create(c -> c
                .index(INDEX_NAME)
                .settings(s -> s
                        .numberOfShards("1")
                        .numberOfReplicas("0"))
                .mappings(m -> m.properties(properties)));
    }

    public void indexGoods(Goods goods) {
        try {
            GoodsDocument doc = GoodsDocument.fromGoods(goods);
            esClient.index(i -> i
                    .index(INDEX_NAME)
                    .id(String.valueOf(goods.getId()))
                    .document(doc));
        } catch (Exception e) {
            log.warn("索引商品失败, goodsId={}: {}", goods.getId(), e.getMessage());
        }
    }

    public void deleteGoods(Long goodsId) {
        try {
            esClient.delete(d -> d
                    .index(INDEX_NAME)
                    .id(String.valueOf(goodsId)));
        } catch (Exception e) {
            log.warn("删除商品索引失败, goodsId={}: {}", goodsId, e.getMessage());
        }
    }

    public void reindexAll() {
        try {
            boolean exists = esClient.indices().exists(
                    ExistsRequest.of(e -> e.index(INDEX_NAME))).value();
            if (exists) {
                esClient.indices().delete(d -> d.index(INDEX_NAME));
            }
            createIndex();

            List<Goods> allGoods = goodsMapper.selectList(
                    new LambdaQueryWrapper<Goods>().eq(Goods::getStatus, 0));

            if (allGoods.isEmpty()) {
                log.info("全量重建索引：没有需要索引的商品");
                return;
            }

            // 批量索引，每批 500 条
            int batchSize = 500;
            for (int i = 0; i < allGoods.size(); i += batchSize) {
                List<Goods> batch = allGoods.subList(i, Math.min(i + batchSize, allGoods.size()));
                List<BulkOperation> operations = new ArrayList<>();
                for (Goods goods : batch) {
                    GoodsDocument doc = GoodsDocument.fromGoods(goods);
                    operations.add(BulkOperation.of(op -> op
                            .index(idx -> idx
                                    .index(INDEX_NAME)
                                    .id(String.valueOf(goods.getId()))
                                    .document(doc))));
                }
                esClient.bulk(BulkRequest.of(b -> b.operations(operations)));
            }

            log.info("全量重建索引完成，共 {} 条商品", allGoods.size());
        } catch (Exception e) {
            log.error("全量重建索引失败: {}", e.getMessage(), e);
        }
    }
}
