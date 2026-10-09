package com.campus.ai.rag;

import com.campus.ai.assistant.CampusRagAssistant;
import com.campus.ai.assistant.IntentClassifier;
import com.campus.ai.assistant.QueryRewriter;
import com.campus.ai.chat.dto.ChatRequest;
import com.campus.ai.chat.dto.ChatResponse;
import com.campus.ai.config.AiProperties;
import com.campus.ai.config.ScoringConfig;
import com.campus.ai.memory.MemoryStore;
import com.campus.ai.retrieval.HybridRetriever;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.scoring.ScoringModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * RagOrchestrator 链路测试（mock retriever / scoringModel / ragAssistant）：
 * 伪造引用整体替换、个别伪造剥离 + correction 事件、低分跳过 LLM、中分免责声明、
 * 零引用降级、P0-1 主路径 token 推送、P1-11 兜底置信度、P1-12 降级分级。
 */
class RagOrchestratorTest {

    private IntentClassifier intentClassifier;
    private QueryRewriter queryRewriter;
    private HybridRetriever retriever;
    private ScoringModel scoringModel;
    private MemoryStore memoryStore;
    private CampusRagAssistant ragAssistant;
    private ChatModel chatModel;
    private AiProperties props;

    @BeforeEach
    void setUp() {
        intentClassifier = mock(IntentClassifier.class);
        queryRewriter = mock(QueryRewriter.class);
        retriever = mock(HybridRetriever.class);
        scoringModel = mock(ScoringModel.class);
        memoryStore = mock(MemoryStore.class);
        ragAssistant = mock(CampusRagAssistant.class);
        chatModel = mock(ChatModel.class);

        props = new AiProperties();
        // 本组用例不关注换词重试：阈值设为极大值使 bestScore > threshold 永不成立
        props.getKnowledge().setScoreThreshold(999d);

        ChatMemory memory = mock(ChatMemory.class);
        when(memory.messages()).thenReturn(List.of());
        when(memoryStore.getOrCreate(anyLong(), anyInt())).thenReturn(memory);
        when(intentClassifier.classify(anyString())).thenReturn("rag");
        when(queryRewriter.rewrite(anyString(), anyList())).thenReturn(List.of("补考重学怎么申请"));
    }

    /** 构造被测对象；scoringModel 可替换（如换 NoOp 实例模拟降级）。 */
    private RagOrchestrator orchestrator(ScoringModel model) {
        return new RagOrchestrator(chatModel, intentClassifier, queryRewriter,
                retriever, model, props, memoryStore, ragAssistant);
    }

    // ── P3-1 抗幻觉链路 ──────────────────────────────────────────────────────────

    @Test
    void majorityFabricatedFallsBackToDigest() {
        givenScores(-0.9);
        givenHits("补考可在开学初申请");
        when(ragAssistant.chatWithContext(anyLong(), anyString(), anyString()))
                .thenReturn("补考可在开学初申请[来源1]，另见[来源99][来源100][来源101]。");

        List<RagOrchestrator.StreamEvent> events = new ArrayList<>();
        ChatResponse resp = orchestrator(scoringModel).run(request(), 1L, events::add);

        // 3/4 引用伪造 → 系统性幻觉 → 整体替换为片段摘要，置信度强制 low（P1-11）
        assertEquals(ConfidenceGrader.LOW, resp.getConfidence());
        assertTrue(resp.getAnswer().contains("在《学生手册》中未找到"));
        assertTrue(resp.getAnswer().contains("建议您联系教务处"));
        Map<?, ?> correction = correctionEvent(events);
        assertEquals(true, correction.get("rejected"));
        assertEquals(List.of(99, 100, 101), correction.get("fabricated"));
        assertFalse(tokenEvents(events).isEmpty());
    }

    @Test
    void minorityFabricatedStripsAndEmitsCorrection() {
        givenScores(-0.9, -0.9);
        givenHits("补考可在开学初申请", "重学需缴费");
        when(ragAssistant.chatWithContext(anyLong(), anyString(), anyString()))
                .thenReturn("补考可在开学初申请[来源1]。另见说明[来源9]。");

        List<RagOrchestrator.StreamEvent> events = new ArrayList<>();
        ChatResponse resp = orchestrator(scoringModel).run(request(), 1L, events::add);

        // 1/2 伪造 ≤ 50% → 不整体替换，剥离 [来源9] 并发 correction（rejected=false）
        assertEquals(ConfidenceGrader.HIGH, resp.getConfidence());
        assertTrue(resp.getAnswer().contains("[来源1]"));
        assertFalse(resp.getAnswer().contains("[来源9]"));
        assertTrue(resp.getAnswer().contains("另见说明"));
        Map<?, ?> correction = correctionEvent(events);
        assertEquals(false, correction.get("rejected"));
        assertEquals(List.of(9), correction.get("fabricated"));
    }

    @Test
    void lowScoreSkipsLlmAndUsesDigest() {
        givenScores(-0.1);
        givenHits("补考可在开学初申请");

        List<RagOrchestrator.StreamEvent> events = new ArrayList<>();
        ChatResponse resp = orchestrator(scoringModel).run(request(), 1L, events::add);

        // 低置信度直接片段摘要，绝不进 LLM
        verify(ragAssistant, never()).chatWithContext(anyLong(), anyString(), anyString());
        assertEquals(ConfidenceGrader.LOW, resp.getConfidence());
        assertTrue(resp.getAnswer().contains("在《学生手册》中未找到"));
        assertFalse(tokenEvents(events).isEmpty());
    }

    @Test
    void mediumScoreGetsDisclaimer() {
        givenScores(-0.45);
        givenHits("补考可在开学初申请");
        when(ragAssistant.chatWithContext(anyLong(), anyString(), anyString()))
                .thenReturn("补考可在开学初申请[来源1]。");

        ChatResponse resp = orchestrator(scoringModel).run(request(), 1L, e -> {});

        assertEquals(ConfidenceGrader.MEDIUM, resp.getConfidence());
        assertTrue(resp.getAnswer().startsWith("（以下信息基于检索片段整理"));
    }

    @Test
    void zeroCitationIsDowngradedNotRejected() {
        givenScores(-0.9);
        givenHits("补考可在开学初申请");
        // 回答无法回溯到任何来源（提示词要求的"建议咨询教务处"类表述）→ 系统补注不会强塞编号
        when(ragAssistant.chatWithContext(anyLong(), anyString(), anyString()))
                .thenReturn("这个问题我无法确定，建议咨询教务处。");

        List<RagOrchestrator.StreamEvent> events = new ArrayList<>();
        ChatResponse resp = orchestrator(scoringModel).run(request(), 1L, events::add);

        // P1-10：零引用不整体替换，补免责声明并把 high 压到 medium；
        // 补注失败时 dropSources：无引用的回答不展示来源列表
        assertTrue(resp.getAnswer().startsWith("（本回答未标注引用来源"));
        assertTrue(resp.getAnswer().contains("建议咨询教务处"));
        assertEquals(ConfidenceGrader.MEDIUM, resp.getConfidence());
        assertTrue(resp.getSources().isEmpty());
        assertTrue(events.stream().noneMatch(e -> "correction".equals(e.type())));
    }

    @Test
    void zeroCitationAutoInjectedSkipsDisclaimer() {
        givenScores(-0.9);
        givenHits("补考可在开学初申请");
        when(ragAssistant.chatWithContext(anyLong(), anyString(), anyString()))
                .thenReturn("补考可在开学初申请。");

        List<RagOrchestrator.StreamEvent> events = new ArrayList<>();
        ChatResponse resp = orchestrator(scoringModel).run(request(), 1L, events::add);

        // 系统补注：零引用回答按句匹配来源后注入 [来源1]，不再加 NO_CITATION_DISCLAIMER，
        // 置信度保持原档，来源列表正常展示（覆盖率 100%）
        assertTrue(resp.getAnswer().contains("补考可在开学初申请[来源1]"));
        assertFalse(resp.getAnswer().contains("本回答未标注引用来源"));
        assertEquals(ConfidenceGrader.HIGH, resp.getConfidence());
        assertEquals(1, resp.getSources().size());
    }

    @Test
    void partialAutoCiteCoveragePressesMedium() {
        givenScores(-0.9);
        givenHits("补考可在开学初申请");
        when(ragAssistant.chatWithContext(anyLong(), anyString(), anyString()))
                .thenReturn("补考可在开学初申请。另外关于奖学金的事情。还有转专业的问题。");

        ChatResponse resp = orchestrator(scoringModel).run(request(), 1L, e -> {});

        // 3 句实质句仅 1 句命中来源（覆盖率 33% < 50%）：仍压 medium 并加轻量核实提示，
        // 但已注入的 [来源1] 保留，不加 NO_CITATION_DISCLAIMER
        assertTrue(resp.getAnswer().contains("补考可在开学初申请[来源1]"));
        assertTrue(resp.getAnswer().contains("系统自动匹配来源"));
        assertFalse(resp.getAnswer().contains("本回答未标注引用来源"));
        assertEquals(ConfidenceGrader.MEDIUM, resp.getConfidence());
    }

    @Test
    void autoCiteDoesNotFabricateOnUnrelatedSentences() {
        givenScores(-0.9);
        givenHits("补考可在开学初申请");
        when(ragAssistant.chatWithContext(anyLong(), anyString(), anyString()))
                .thenReturn("补考可在开学初申请。今天天气不错。");

        ChatResponse resp = orchestrator(scoringModel).run(request(), 1L, e -> {});

        // 伪造注入防线：无关句子（"今天天气不错"）不得强塞编号；仅相关句补注
        assertTrue(resp.getAnswer().contains("补考可在开学初申请[来源1]"));
        assertFalse(resp.getAnswer().contains("天气[来源"));
        assertFalse(resp.getAnswer().contains("本回答未标注引用来源"));
    }

    // ── P0-1 流式主路径 token 推送 ───────────────────────────────────────────────

    @Test
    void mainPathPushesFinalAnswerToken() {
        givenScores(-0.9);
        givenHits("补考可在开学初申请");
        when(ragAssistant.chatWithContext(anyLong(), anyString(), anyString()))
                .thenReturn("补考可在开学初申请[来源1]。");

        List<RagOrchestrator.StreamEvent> events = new ArrayList<>();
        ChatResponse resp = orchestrator(scoringModel).run(request(), 1L, events::add);

        // 高/中置信度主路径必须推送 token，否则 /chat/stream 用户看不到回答
        List<RagOrchestrator.StreamEvent> tokens = tokenEvents(events);
        assertEquals(1, tokens.size());
        assertEquals(resp.getAnswer(), tokens.get(0).payload());
        assertTrue(events.stream().anyMatch(e -> "done".equals(e.type())));
    }

    // ── P1-11 兜底置信度一致性 ───────────────────────────────────────────────────

    @Test
    void llmFailureDigestForcesLowConfidence() {
        givenScores(-0.9);
        givenHits("补考可在开学初申请");
        // 不可重试异常（4xx），避免 callWithRetry 退避等待拖慢测试
        when(ragAssistant.chatWithContext(anyLong(), anyString(), anyString()))
                .thenThrow(new RuntimeException("400 参数错误"));

        ChatResponse resp = orchestrator(scoringModel).run(request(), 1L, e -> {});

        // LLM 失败走 digest 兜底，置信度强制 low（不能仍报 high）
        assertEquals(ConfidenceGrader.LOW, resp.getConfidence());
        assertTrue(resp.getAnswer().contains("在《学生手册》中未找到"));
    }

    // ── P1-12 NoOp 降级 ──────────────────────────────────────────────────────────

    @Test
    void degradedRerankerForcesMediumAndAnnotates() {
        // 真实 NoOpScoringModel（恒等分 0,-1,-2…）模拟 reranker 缺失
        givenHits("补考可在开学初申请");
        when(ragAssistant.chatWithContext(anyLong(), anyString(), anyString()))
                .thenReturn("补考可在开学初申请[来源1]。");

        ChatResponse resp = orchestrator(new ScoringConfig.NoOpScoringModel())
                .run(request(), 1L, e -> {});

        // 恒等分会判 low（全量跳过 LLM）或误判 high；降级强制 medium 并标注降级运行
        assertEquals(ConfidenceGrader.MEDIUM, resp.getConfidence());
        assertTrue(resp.getAnswer().contains("降级运行"));
        verify(ragAssistant).chatWithContext(anyLong(), anyString(), anyString());
    }

    // ── 极性出口：raw logits 归一化后才进分级 ────────────────────────────────────

    @Test
    void rawLogitScoresAreNormalizedBeforeGrading() {
        // 打分模型吐 raw logits（越高越相关）：相关对 -0.63 经 NormalizedScoringModel
        // 归一化为 ≈-0.73 应判 high；若极性未在出口翻转会误判 low
        ScoringModel rawLogitModel = mock(ScoringModel.class);
        when(rawLogitModel.scoreAll(anyList(), anyString()))
                .thenReturn(Response.from(new ArrayList<>(List.of(-0.63))));
        givenHits("补考可在开学初申请");
        when(ragAssistant.chatWithContext(anyLong(), anyString(), anyString()))
                .thenReturn("补考可在开学初申请[来源1]。");

        ChatResponse resp = orchestrator(new ScoringConfig.NormalizedScoringModel(rawLogitModel))
                .run(request(), 1L, e -> {});

        assertEquals(ConfidenceGrader.HIGH, resp.getConfidence());
    }

    // ── 测试脚手架 ───────────────────────────────────────────────────────────────

    /** 注入重排序分数：直接给归一化域 [-1,0]（越低越好）的分数，绕过 ScoringConfig 出口包装。 */
    private void givenScores(double... scores) {
        List<Double> list = new ArrayList<>();
        for (double d : scores) list.add(d);
        when(scoringModel.scoreAll(anyList(), anyString())).thenReturn(Response.from(list));
    }

    private void givenHits(String... contents) {
        List<HybridRetriever.Hit> hits = new ArrayList<>();
        for (int i = 0; i < contents.length; i++) {
            hits.add(new HybridRetriever.Hit("学生手册.md", i, contents[i], -0.5, ""));
        }
        when(retriever.searchMulti(anyList(), anyInt())).thenReturn(hits);
    }

    private ChatRequest request() {
        ChatRequest req = new ChatRequest();
        req.setQuestion("补考重学怎么申请");
        req.setSessionId(1L);
        return req;
    }

    private List<RagOrchestrator.StreamEvent> tokenEvents(List<RagOrchestrator.StreamEvent> events) {
        return events.stream().filter(e -> "token".equals(e.type())).toList();
    }

    private Map<?, ?> correctionEvent(List<RagOrchestrator.StreamEvent> events) {
        return events.stream()
                .filter(e -> "correction".equals(e.type()))
                .map(e -> (Map<?, ?>) e.payload())
                .findFirst()
                .orElseThrow(() -> new AssertionError("缺少 correction 事件"));
    }
}
