package com.campus.ai.retrieval;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SynonymExpander 规则同义扩展测试：
 * 示例办事查询（学分认定 / 挂科重修流程）应扩展出手册正式用词的变体。
 */
class SynonymExpanderTest {

    private final SynonymExpander expander = new SynonymExpander();

    @Test
    void expandsCreditRecognitionQuery() {
        // "学分认定" 撞不上手册的 "学分转换/课程替代/学分互认" 用词
        List<String> out = expander.expand("学分认定");
        assertEquals("学分认定", out.get(0), "首元素必须是原查询");
        assertTrue(out.contains("学分转换"), "应扩展出 学分转换");
        assertTrue(out.contains("课程替代"), "应扩展出 课程替代");
        assertTrue(out.contains("学分互认"), "应扩展出 学分互认");
    }

    @Test
    void expandsFailedCourseQueryWithSurroundingWordsKept() {
        // "挂科重修流程"：挂科 → 不及格/补考/重学，其余字词原样保留
        List<String> out = expander.expand("挂科重修流程");
        assertEquals("挂科重修流程", out.get(0));
        assertTrue(out.stream().anyMatch(q -> q.contains("不及格")), "应扩展出 不及格 变体");
        assertTrue(out.stream().anyMatch(q -> q.contains("补考")), "应扩展出 补考 变体");
        assertTrue(out.stream().anyMatch(q -> q.contains("重学")), "应扩展出 重学 变体");
    }

    @Test
    void returnsOnlyOriginalWhenNoDictionaryHit() {
        List<String> out = expander.expand("图书馆开放时间");
        assertEquals(List.of("图书馆开放时间"), out);
    }

    @Test
    void capsVariantCountAndDeduplicates() {
        // 同一查询多次扩展结果稳定，且变体数受上限约束
        List<String> out = expander.expand("挂科重修");
        assertEquals(out, expander.expand("挂科重修"));
        assertTrue(out.size() <= 5, "原查询 + 变体总量应受上限约束，实际 " + out.size());
        assertEquals(out.size(), out.stream().distinct().count(), "变体不应重复");
    }

    @Test
    void blankQueryReturnsEmpty() {
        assertTrue(expander.expand(null).isEmpty());
        assertTrue(expander.expand("   ").isEmpty());
    }
}
