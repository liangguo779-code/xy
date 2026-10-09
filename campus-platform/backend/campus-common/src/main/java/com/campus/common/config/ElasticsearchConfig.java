package com.campus.common.config;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.ElasticsearchTransport;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Elasticsearch 客户端配置（商品搜索、论坛搜索、AI 向量库共用）。
 * 置于 campus-common 使 trade/forum/ai 各模块共享同一个 Bean，避免重复定义。
 */
@Configuration
public class ElasticsearchConfig {

    /** ES 节点地址（如 localhost） */
    @Value("${elasticsearch.host}")
    private String host;

    /** ES HTTP 端口（默认 9200） */
    @Value("${elasticsearch.port}")
    private int port;

    /** 构建并注册 ElasticsearchClient Bean（JSON 序列化走 Jackson） */
    @Bean
    public ElasticsearchClient elasticsearchClient() {
        RestClient restClient = RestClient.builder(new HttpHost(host, port)).build();
        // 必须注册 JavaTimeModule 并以 ISO-8601 输出日期：默认 mapper 序列化 LocalDateTime
        // 会抛 "Jackson exception"（帖子索引一直写不进去的根因）；findAndRegisterModules 发现
        // classpath 上的 jsr310 模块，禁用时间戳输出保证 ES date 字段按 ISO 解析
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper()
                .findAndRegisterModules()
                .disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        ElasticsearchTransport transport = new RestClientTransport(restClient, new JacksonJsonpMapper(mapper));
        return new ElasticsearchClient(transport);
    }
}
