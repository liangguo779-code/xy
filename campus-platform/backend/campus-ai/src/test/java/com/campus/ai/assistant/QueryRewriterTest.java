package com.campus.ai.assistant;

import com.campus.ai.retrieval.SynonymExpander;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * QueryRewriter 短查询路径测试：
 * ≤30 字不调用 LLM，退化为规则同义扩展；空问题返回空列表。
 */
class QueryRewriterTest {

    /** 短查询不应触达 LLM；一旦调用即失败。 */
    private final QueryRewriter rewriter = new QueryRewriter(userContent -> {
        throw new IllegalStateException("短查询不应调用 LLM: " + userContent);
    }, new SynonymExpander());

    @Test
    void shortQueryExpandsSynonymsWithoutLlm() {
        List<String> out = rewriter.rewrite("学分认定", null);
        assertEquals("学分认定", out.get(0), "首元素必须是原查询");
        assertTrue(out.stream().anyMatch(q -> q.contains("学分转换")),
                "短查询应做规则同义扩展以命中手册用词，实际: " + out);
    }

    @Test
    void shortQueryWithoutHitReturnsOriginalOnly() {
        assertEquals(List.of("图书馆开放时间"), rewriter.rewrite("图书馆开放时间", null));
    }

    @Test
    void blankQuestionReturnsEmpty() {
        assertTrue(rewriter.rewrite(null, null).isEmpty());
        assertTrue(rewriter.rewrite("  ", null).isEmpty());
    }
}
