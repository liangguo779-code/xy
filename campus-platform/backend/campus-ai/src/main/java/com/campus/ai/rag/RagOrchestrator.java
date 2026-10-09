package com.campus.ai.rag;

import com.campus.ai.assistant.CampusRagAssistant;
import com.campus.ai.assistant.IntentClassifier;
import com.campus.ai.assistant.QueryRewriter;
import com.campus.ai.config.AiProperties;
import com.campus.ai.config.ScoringConfig;
import com.campus.ai.chat.dto.ChatRequest;
import com.campus.ai.chat.dto.ChatResponse;
import com.campus.ai.memory.MemoryStore;
import com.campus.ai.retrieval.HybridRetriever;
import dev.langchain4j.data.message.*;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.scoring.ScoringModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.function.Consumer;

/**
 * RAG 总调度器。
 *
 * <p>职责：意图分类 → 查询改写 → 混合检索 → 重排序 → 重试 → 生成回答 / 兜底。
 *
 * <p>LLM 生成委托给 {@link CampusRagAssistant}（LangChain4j AiServices），
 * 由框架自动管理 ChatMemory、工具调用和提示词。
 * 本类负责检索流水线和反幻觉防御（置信度分级 + 引用校验）。
 *
 * <p>如果没有这个文件：
 * <ul>
 *   <li>无法将用户的原始问题经过意图过滤、检索增强、置信度判断后生成有依据的回答</li>
 *   <li>缺少低置信度时跳过 LLM 直接返回片段的兜底策略</li>
 *   <li>缺少引用校验，LLM 可能编造不存在的来源编号</li>
 *   <li>SSE 流式推送的各阶段状态事件将丢失，前端无法展示"正在检索…""正在排序…"等进度</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RagOrchestrator {

    /** 检索关键词重试最大次数（分数不理想时换词再检索一次）。 */
    private static final int MAX_RETRIES = 1;
    /** 参与对话的历史消息窗口条数（仅取最近 N 条，防止上下文过长）。 */
    private static final int HISTORY_WINDOW = 20;
    /** 重排序后保留的候选片段数。 */
    private static final int RERANK_TOPK = 10;
    /** 低置信度兜底摘要展示的片段条数。 */
    private static final int LOW_DIGEST_TOPN = 3;

    /** LLM 调用最大重试次数（针对临时故障：5xx、网络超时、连接重置）。 */
    private static final int LLM_MAX_RETRIES = 2;
    /** LLM 重试基础等待时间（毫秒），指数退避：1s → 2s → 4s。 */
    private static final long LLM_RETRY_BASE_MS = 1000L;

    /** 闲聊意图的 system prompt：限制回复长度并引导回校园话题，避免模型跑题。 */
    private static final String CHAT_REPLY_PROMPT = """
            你是校园事务咨询助手。用户在和你闲聊，请简短友好地回复，然后引导用户提出校园相关的问题。回复不超过两句话。""";

    /** 意图被判定为"无关问题"时的固定拒答文案。 */
    private static final String REJECT_ANSWER = "抱歉，我只能回答校园事务相关的问题（如规章制度、课程考试、奖学金、休学等）。如果你有校园相关的问题，随时可以问我！";
    /** 检索无结果或 LLM 兜底时的固定文案，引导用户线下咨询。 */
    private static final String FALLBACK_ANSWER = "同学你好，在《学生手册》中未查询到相关规定。该问题可能涉及教务处具体业务，建议您联系教务处或学生事务中心咨询。";

    /** 置信度为"中"时，追加到检索上下文前面的提示语。 */
    private static final String MEDIUM_PROMPT_ADDENDUM = """
            【置信度提示】本次检索结果与问题匹配度一般。请严格依据参考信息回答；若参考信息无法确认，务必明确说"无法确定"，不要硬编内容，也不要引用未列出的来源编号。""";

    /** 中置信度回答前缀免责声明，提醒用户信息需二次确认。 */
    private static final String MEDIUM_DISCLAIMER = "（以下信息基于检索片段整理，建议联系教务处或学生事务中心二次确认）\n\n";
    /** 低置信度兜底摘要的前缀，明示片段相关性较低。 */
    private static final String LOW_DISCLAIMER = "在《学生手册》中未找到与该问题高度匹配的内容，以下片段相关性较低，仅供参考：\n\n";
    /** 零引用降级免责声明（P1-10）：无引用不再整体拒绝，改为提示用户核实并降置信度。 */
    private static final String NO_CITATION_DISCLAIMER = "（本回答未标注引用来源，建议核实后参考）\n\n";
    /** 自动补注覆盖率下限：低于该值视为多数结论句无法回溯来源，仍压 medium 并加轻量核实提示。 */
    private static final double AUTO_CITE_COVERAGE_FLOOR = 0.5d;
    /**
     * 自动补注覆盖率不足时的轻量核实提示：已按句匹配到部分来源（区别于 NO_CITATION_DISCLAIMER）。
     * 以"（"开头，沿用零引用免责声明的前缀语义，MEDIUM_DISCLAIMER 不再重复叠加。
     */
    private static final String PARTIAL_CITE_NOTICE = "（部分结论已由系统自动匹配来源，其余内容未标注，建议核实后参考）\n\n";
    /** 重排序降级运行提示（P1-12）：reranker 缺失时置信度分级不可信，明示用户。 */
    private static final String DEGRADED_NOTICE = "（重排序模型缺失，本次回答为降级运行，仅供参考）\n\n";

    /** SSE 阶段事件 code → 前端展示文案的映射，供流式推送进度提示使用。 */
    public static final Map<String, String> STAGE_MESSAGES = Map.ofEntries(
            Map.entry("classify_done", "正在分析问题意图..."),
            Map.entry("rewrite_done", "正在优化搜索词..."),
            Map.entry("retrieve_done", "正在检索相关信息..."),
            Map.entry("rerank_done", "正在排序匹配结果..."),
            Map.entry("retry_done", "正在尝试其他关键词..."),
            Map.entry("generate_done", "正在整理回答..."),
            Map.entry("citation_validated", "正在校验引用..."),
            Map.entry("low_confidence", "正在整理回答..."));

    private final ChatModel chatModel;
    private final IntentClassifier intentClassifier;
    private final QueryRewriter queryRewriter;
    private final HybridRetriever retriever;
    /**
     * 重排序打分模型（cross-encoder）。分数越低表示与问题越匹配 ——
     * OnnxScoringModel 原始 raw logits 是越高越相关，已在 ScoringConfig 出口归一化为 [-1,0] 越低越好。
     */
    private final ScoringModel scoringModel;
    private final AiProperties props;
    private final MemoryStore memoryStore;
    private final CampusRagAssistant ragAssistant;

    // ── 公共入口 ─────────────────────────────────────────────────────────────────

    /**
     * RAG 主流程入口：意图分类 → 检索改写 → 混合检索 → 重排序 → 重试 → 生成/兜底。
     *
     * @param request 用户提问请求
     * @param sessionId 会话 ID，用于关联对话记忆
     * @param sink    SSE 流式事件回调，推送阶段进度 / token / 来源
     * @return 最终回答（含来源列表与置信度分级）
     */
    public ChatResponse run(ChatRequest request, Long sessionId, Consumer<StreamEvent> sink) {
        List<ChatMessage> history = memoryStore.getOrCreate(sessionId, HISTORY_WINDOW).messages();
        State s = new State(request.getQuestion(), history, sessionId);

        // 意图分类与查询改写并行执行（互不依赖），降低首包延迟。
        var intentFut = java.util.concurrent.CompletableFuture.supplyAsync(
                () -> intentClassifier.classify(s.question));
        var rewriteFut = java.util.concurrent.CompletableFuture.supplyAsync(
                () -> queryRewriter.rewrite(s.question, s.history));
        s.intent = intentFut.join();
        sink.accept(new StreamEvent("stage", "classify_done", STAGE_MESSAGES.get("classify_done")));

        switch (s.intent) {
            case "chat" -> {
                String reply = chatReply(s.question);
                sink.accept(new StreamEvent("token", null, reply));
                sink.accept(new StreamEvent("done", null, null));
                return buildResponse(reply, List.of(), null, sessionId);
            }
            case "reject" -> {
                sink.accept(new StreamEvent("token", null, REJECT_ANSWER));
                sink.accept(new StreamEvent("done", null, null));
                return buildResponse(REJECT_ANSWER, List.of(), null, sessionId);
            }
        }

        // ── 检索流水线 ────────────────────────────────────────────────────────────
        s.queries = rewriteFut.join();
        sink.accept(new StreamEvent("stage", "rewrite_done", STAGE_MESSAGES.get("rewrite_done")));

        s.searchResults = retriever.searchMulti(s.queries, props.getVector().getRetrieveTopk());
        log.info("检索完成: queries={}, 结果数={}", s.queries, s.searchResults.size());
        sink.accept(new StreamEvent("stage", "retrieve_done", STAGE_MESSAGES.get("retrieve_done")));

        List<Ranked> ranked = rerankWithScores(s.question, s.searchResults, RERANK_TOPK);
        s.searchResults = ranked.stream().map(Ranked::hit).toList();
        s.bestScore = ranked.isEmpty() ? ConfidenceGrader.NO_RESULT : ranked.get(0).rerankScore();
        // P1-12：reranker 缺失（NoOp 恒等分）时置降级标志，分级强制 medium/low
        s.rerankDegraded = scoringModel instanceof ScoringConfig.NoOpScoringModel;
        s.confidence = ConfidenceGrader.grade(s.bestScore, props, s.rerankDegraded);
        log.info("置信度={}, topRerankScore={}, rerankDegraded={}", s.confidence, s.bestScore, s.rerankDegraded);
        sink.accept(new StreamEvent("stage", "rerank_done", STAGE_MESSAGES.get("rerank_done")));

        // 分数不理想时重试一次：归一化后分数越低越好（raw logits 越高越相关已在 ScoringConfig 出口翻转），
        // 故 bestScore > threshold 视为"匹配差"。
        // 重试策略是让 LLM 换一组关键词再检索，结果按 source_chunkIndex 去重合并（保留分数更低/更好的那条）。
        double threshold = props.getKnowledge().getScoreThreshold();
        if (s.bestScore > threshold && s.retryCount < MAX_RETRIES) {
            s.retryCount++;
            String newQuery = retryKeyword(s.queries.get(0), s.bestScore);
            List<HybridRetriever.Hit> extra = retriever.searchMulti(List.of(newQuery), props.getVector().getRetrieveTopk());
            Map<String, HybridRetriever.Hit> merged = new LinkedHashMap<>();
            for (HybridRetriever.Hit h : s.searchResults) merged.put(h.source() + "_" + h.chunkIndex(), h);
            for (HybridRetriever.Hit h : extra) {
                String key = h.source() + "_" + h.chunkIndex();
                HybridRetriever.Hit existing = merged.get(key);
                if (existing == null || h.score() < existing.score()) merged.put(key, h);
            }
            s.searchResults = new ArrayList<>(merged.values()).subList(0,
                    Math.min(props.getVector().getRetrieveTopk(), merged.size()));
            ranked = rerankWithScores(s.question, s.searchResults, RERANK_TOPK);
            s.searchResults = ranked.stream().map(Ranked::hit).toList();
            s.bestScore = ranked.isEmpty() ? ConfidenceGrader.NO_RESULT : ranked.get(0).rerankScore();
            s.confidence = ConfidenceGrader.grade(s.bestScore, props, s.rerankDegraded);
            log.info("重试后 置信度={}, topRerankScore={}", s.confidence, s.bestScore);
            sink.accept(new StreamEvent("stage", "retry_done", STAGE_MESSAGES.get("retry_done")));
        }

        if (s.searchResults.isEmpty()) {
            sink.accept(new StreamEvent("token", null, FALLBACK_ANSWER));
            sink.accept(new StreamEvent("done", null, null));
            return buildResponse(FALLBACK_ANSWER, List.of(), ConfidenceGrader.LOW, sessionId);
        }

        List<ChatResponse.SourceItem> sources = buildSources(s.searchResults);

        // 低置信度 → 跳过 LLM，直接展示 top 片段 + 兜底提示
        if (ConfidenceGrader.LOW.equals(s.confidence)) {
            sink.accept(new StreamEvent("stage", "low_confidence", STAGE_MESSAGES.get("low_confidence")));
            String lowAnswer = streamLowConfidenceDigest(sources, sink);
            sink.accept(new StreamEvent("sources", null, sources));
            sink.accept(new StreamEvent("done", null, null));
            return buildResponse(lowAnswer, sources, ConfidenceGrader.LOW, sessionId);
        }

        // ── 通过 AiServices 生成回答（ChatMemory + 工具调用自动管理）──────────────
        String answer = generateAnswer(s, sources, sink);
        sink.accept(new StreamEvent("stage", "generate_done", STAGE_MESSAGES.get("generate_done")));
        // 零引用回答不展示来源列表：无关分块被当成依据展示会误导用户（校园卡案例）。
        List<ChatResponse.SourceItem> shown = s.dropSources ? List.of() : sources;
        sink.accept(new StreamEvent("sources", null, shown));
        sink.accept(new StreamEvent("done", null, null));
        return buildResponse(answer, shown, s.confidence, sessionId);
    }

    // ── 生成回答 ─────────────────────────────────────────────────────────────────

    /**
     * 拼接检索上下文并调用 LLM 生成回答，失败或空回答时回退到片段摘要。
     * 中置信度时在上下文前插入"严格依据参考信息"的约束提示，抑制幻觉。
     */
    private String generateAnswer(State s, List<ChatResponse.SourceItem> sources, Consumer<StreamEvent> sink) {
        StringBuilder context = new StringBuilder();
        for (var src : sources) {
            context.append("[来源").append(src.getIndex()).append("] 来自《").append(src.getSource()).append("》:\n")
                    .append(src.getContent()).append("\n\n---\n\n");
        }
        if (ConfidenceGrader.MEDIUM.equals(s.confidence)) {
            context.insert(0, MEDIUM_PROMPT_ADDENDUM + "\n");
        }

        String answer;
        try {
            // 真流式：TokenStream 逐 token 推送（sink 增量转发），阻塞至生成完成。
            answer = callWithRetry(
                    () -> streamWithContext(s, context.toString(), sink),
                    "LLM 生成回答");
        } catch (Exception e) {
            log.error("LLM 生成回答最终失败: {}", e.getMessage());
            answer = null;
        }

        if (answer != null && !answer.isEmpty()) {
            sink.accept(new StreamEvent("stage", "citation_validated", STAGE_MESSAGES.get("citation_validated")));
            return postProcessAnswer(s, answer, sources, sink);
        }

        log.warn("LLM returned empty/null answer, using fallback digest");
        // P1-11：LLM 失败走 digest 兜底，可信度强制降为 low，避免前端标签误导
        s.confidence = ConfidenceGrader.LOW;
        return streamLowConfidenceDigest(sources, sink);
    }

    /**
     * 生成后处理：引用校验 + 可信度修饰。
     * 系统性幻觉（伪造引用占比过高）直接丢弃 LLM 回答改用片段摘要（置信度强制 low，P1-11）；
     * 个别伪造引用只剥离标记保留正文；零引用先做系统补注（按句匹配来源注入 [来源N]），
     * 补注失败才降级（免责声明 + 降 medium，P1-10）；中置信度追加免责声明。
     * 流式路径下 token 已逐条推送，最终文本与原始文本不一致时以 correction 事件
     * 携带 cleaned 文本交前端替换，避免整段重复推送。
     */
    private String postProcessAnswer(State s, String rawAnswer, List<ChatResponse.SourceItem> sources,
                                     Consumer<StreamEvent> sink) {
        // P1-9：传入来源正文做内容级校验，编号合法但"张冠李戴"的引用同样计入伪造占比
        List<String> contents = sources.stream().map(ChatResponse.SourceItem::getContent).toList();
        CitationValidator.Result v = CitationValidator.validate(rawAnswer, contents);
        if (CitationValidator.shouldReject(v.totalCitations(), v.fabricatedCount())) {
            log.warn("引用校验拒绝: total={}, fabricated={}", v.totalCitations(), v.fabricatedCount());
            emitCorrection(sink, v, true);
            // P1-11：整体替换为 digest 兜底时置信度强制 low
            s.confidence = ConfidenceGrader.LOW;
            return streamLowConfidenceDigest(sources, sink);
        }
        String finalAnswer = v.cleanedAnswer();
        if (v.fabricatedCount() > 0) {
            log.warn("引用校验剥离: fabricated={}", v.fabricatedIndices());
            emitCorrection(sink, v, false);
        }
        // P1-10：零引用不再整体拒绝，先尝试系统补注（按句与来源做字面相似度匹配，句末注入 [来源N]）。
        // 一句都没匹配上才走零引用降级，并标记 dropSources：回答没有引用任何来源，
        // 就不应展示"参考来源"列表（否则无关分块会被当成依据展示，误导用户）。
        if (v.totalCitations() == 0) {
            finalAnswer = autoCiteOrDowngrade(s, finalAnswer, contents);
        }
        if (ConfidenceGrader.MEDIUM.equals(s.confidence) && !finalAnswer.startsWith("（")) {
            finalAnswer = MEDIUM_DISCLAIMER + finalAnswer;
        }
        // P1-12：降级运行明示用户
        if (s.rerankDegraded) {
            finalAnswer = DEGRADED_NOTICE + finalAnswer;
        }
        // 流式路径：token 已逐条推送，最终文本被修饰时以 correction 携带 cleaned 供前端整体替换
        // （fabricated>0 时已由 emitCorrection 发过，不重复发）；
        // 非流式路径（同步回退/测试环境）：补发一次最终 token（P0-1）。
        if (s.streamedTokens) {
            if (!finalAnswer.equals(rawAnswer) && v.fabricatedCount() == 0) {
                sink.accept(new StreamEvent("correction", null, Map.of(
                        "cleaned", finalAnswer,
                        "fabricated", List.of(),
                        "strippedCount", 0,
                        "rejected", false)));
            }
        } else {
            sink.accept(new StreamEvent("token", null, finalAnswer));
        }
        return finalAnswer;
    }

    /**
     * 零引用自动补注（系统补注）+ 降级决策。委托 {@link CitationValidator#autoCite}
     * 按句匹配来源并在句末注入 {@code [来源N]}，随后复跑解析校验确认注入编号可解析。
     *
     * <p>注入的标记是"系统补注"而非模型输出：本方法不按"模型伪造被修正"发 correction 事件；
     * 流式路径下补注后全文与模型原文不一致时，由 postProcessAnswer 末尾的统一 correction
     * 通道携带 cleaned 文本交前端整体替换（若后续要区分事件语义，给 payload 加类型字段即可）。
     *
     * <p>决策口径（覆盖率 = 被补注句子 / 实质内容句子）：
     * <ul>
     *   <li>一句都没匹配上：保持现状 —— 零引用免责声明 + 压 medium + dropSources；</li>
     *   <li>补注成功且覆盖率 ≥ {@link #AUTO_CITE_COVERAGE_FLOOR}：不加零引用免责声明，
     *       置信度保持原档 —— 每个被补注的句子都经过字面相似度把关（与反伪造同一套度量），
     *       引用有据可依，来源列表正常展示；</li>
     *   <li>补注成功但覆盖率 &lt; {@link #AUTO_CITE_COVERAGE_FLOOR}：多数结论句无法回溯到来源，
     *       仍压 medium 并加轻量核实提示（{@link #PARTIAL_CITE_NOTICE}）。</li>
     * </ul>
     */
    private String autoCiteOrDowngrade(State s, String answer, List<String> contents) {
        CitationValidator.AutoCite ac = CitationValidator.autoCite(answer, contents);
        if (ac.injectedSentences() == 0) {
            // 一句都没匹配上：保持零引用降级现状
            s.dropSources = true;
            if (ConfidenceGrader.HIGH.equals(s.confidence)) s.confidence = ConfidenceGrader.MEDIUM;
            return NO_CITATION_DISCLAIMER + answer;
        }
        // 复跑解析校验，确认注入编号可解析（仅编号范围/格式校验，不再按内容阈值剥离 ——
        // 内容把关已在匹配阶段完成，若按反伪造的 0.2 阈值复检会把合法补注"回滚"掉）
        CitationValidator.Result v2 = CitationValidator.validate(ac.annotatedAnswer(), contents.size());
        String annotated = v2.totalCitations() > 0 ? v2.cleanedAnswer() : ac.annotatedAnswer();
        log.info("零引用自动补注: 注入句数={}/{}, 覆盖率={}", ac.injectedSentences(),
                ac.substantiveSentences(), String.format("%.0f%%", ac.coverage() * 100));
        if (ac.coverage() < AUTO_CITE_COVERAGE_FLOOR) {
            // 覆盖率不足：多数句子没有出处，仍压 medium 并加轻量核实提示
            if (ConfidenceGrader.HIGH.equals(s.confidence)) s.confidence = ConfidenceGrader.MEDIUM;
            return PARTIAL_CITE_NOTICE + annotated;
        }
        // 覆盖率充足：不加零引用免责声明，置信度保持原档，来源列表正常展示（不设 dropSources）
        return annotated;
    }

    /**
     * 真流式生成：调用 TokenStream 逐 token 推送（onPartialResponse 增量转发），
     * 阻塞至 onCompleteResponse 后返回完整文本。
     * 兼容 langchain4j 回调的两种语义：增量（delta）与累计（cumulative），
     * 通过"前缀匹配"自动识别，防止整段重复推送。
     */
    private String streamWithContext(State s, String context, Consumer<StreamEvent> sink) {
        dev.langchain4j.service.TokenStream stream;
        try {
            stream = ragAssistant.chatWithContextStream(s.sessionId, context, s.question);
        } catch (Exception e) {
            stream = null;
        }
        if (stream == null) {
            log.warn("流式生成不可用，回退同步生成");
            return ragAssistant.chatWithContext(s.sessionId, context, s.question);
        }

        java.util.concurrent.CountDownLatch done = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.atomic.AtomicReference<String> full = new java.util.concurrent.atomic.AtomicReference<>("");
        java.util.concurrent.atomic.AtomicReference<RuntimeException> err = new java.util.concurrent.atomic.AtomicReference<>();
        StringBuilder acc = new StringBuilder();

        try {
            stream.onPartialResponse(part -> {
                        if (part == null || part.isEmpty()) return;
                        String delta;
                        if (part.length() >= acc.length() && part.startsWith(acc.toString())) {
                            // 累计式回调：取新增后缀
                            delta = part.substring(acc.length());
                            acc.setLength(0);
                            acc.append(part);
                        } else {
                            // 增量式回调
                            acc.append(part);
                            delta = part;
                        }
                        if (!delta.isEmpty()) {
                            s.streamedTokens = true;
                            sink.accept(new StreamEvent("token", null, delta));
                        }
                    })
                    .onCompleteResponse(resp -> {
                        String text = resp != null && resp.aiMessage() != null ? resp.aiMessage().text() : null;
                        full.set(text != null && !text.isEmpty() ? text : acc.toString());
                        done.countDown();
                    })
                    .onError(e -> {
                        err.set(new RuntimeException(e));
                        done.countDown();
                    })
                    .start();
        } catch (Exception e) {
            log.warn("流式链启动失败，回退同步生成: {}", e.getMessage());
            return ragAssistant.chatWithContext(s.sessionId, context, s.question);
        }

        try {
            if (!done.await(90, java.util.concurrent.TimeUnit.SECONDS)) {
                throw new RuntimeException("LLM 流式生成超时");
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("LLM 流式生成被中断", ie);
        }
        if (err.get() != null) throw err.get();
        return full.get();
    }

    // ── 辅助方法 ─────────────────────────────────────────────────────────────────

    /**
     * 带指数退避的 LLM 调用重试。
     *
     * <p>外部 LLM API 不可信任，可能因 5xx、网络抖动、连接重置等临时故障失败。
     * 本方法对可重试异常（IOException、HTTP 5xx、超时）进行重试，
     * 对不可重试异常（4xx 客户端错误、认证失败）直接抛出。
     */
    private <T> T callWithRetry(java.util.function.Supplier<T> action, String label) {
        RuntimeException lastException = null;
        for (int attempt = 0; attempt <= LLM_MAX_RETRIES; attempt++) {
            try {
                return action.get();
            } catch (Exception e) {
                lastException = wrapAsRuntime(e);
                if (!isRetryable(e) || attempt == LLM_MAX_RETRIES) {
                    log.error("{} 失败（第{}次）: {}", label, attempt + 1, e.getMessage());
                    throw lastException;
                }
                long waitMs = LLM_RETRY_BASE_MS * (1L << attempt);
                log.warn("{} 失败（第{}次），{}ms 后重试: {}", label, attempt + 1, waitMs, e.getMessage());
                try { Thread.sleep(waitMs); } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw lastException;
                }
            }
        }
        throw lastException;
    }

    /** 将异常包装为 RuntimeException，避免受检异常编译问题。 */
    private static RuntimeException wrapAsRuntime(Exception e) {
        if (e instanceof RuntimeException re) return re;
        return new RuntimeException(e.getMessage(), e);
    }

    /**
     * 判断异常是否可重试。
     * 可重试：网络异常（IOException）、超时、连接重置、HTTP 5xx。
     * 不可重试：HTTP 4xx（客户端错误）、认证失败、参数错误。
     */
    private boolean isRetryable(Exception e) {
        // 网络层异常（连接超时、连接重置、DNS 失败等）
        if (e instanceof java.io.IOException) return true;
        // 超时异常
        if (e instanceof java.util.concurrent.TimeoutException) return true;
        String msg = e.getMessage();
        if (msg == null) return false;
        // HTTP 5xx（服务端临时错误）
        if (msg.contains("5") && msg.contains("status")) return true;
        // 连接相关
        if (msg.contains("Connection") || msg.contains("timeout") || msg.contains("reset")) return true;
        return false;
    }

    private void emitCorrection(Consumer<StreamEvent> sink, CitationValidator.Result v, boolean rejected) {
        sink.accept(new StreamEvent("correction", null, Map.of(
                "cleaned", v.cleanedAnswer(),
                "fabricated", v.fabricatedIndices(),
                "strippedCount", v.fabricatedCount(),
                "rejected", rejected)));
    }

    private ChatResponse buildResponse(String answer, List<ChatResponse.SourceItem> sources, String confidence, Long sessionId) {
        ChatResponse r = new ChatResponse();
        r.setAnswer(answer);
        r.setSources(sources);
        r.setConfidence(confidence);
        r.setSessionId(sessionId);
        return r;
    }

    /** 取前 5 条命中构建来源列表，index 从 1 开始，对应回答中的 [来源N] 编号。 */
    private List<ChatResponse.SourceItem> buildSources(List<HybridRetriever.Hit> hits) {
        List<ChatResponse.SourceItem> sources = new ArrayList<>();
        int limit = Math.min(5, hits.size());
        for (int i = 0; i < limit; i++) {
            HybridRetriever.Hit h = hits.get(i);
            String display = h.sectionPath() != null && !h.sectionPath().isEmpty() ? h.sectionPath() : h.source();
            sources.add(ChatResponse.SourceItem.builder()
                    .index(i + 1).source(display).chunkIndex(h.chunkIndex())
                    .content(h.content()).score(h.score()).build());
        }
        return sources;
    }

    /** 低置信度兜底摘要：跳过 LLM，截取 top 片段（每段 300 字）拼上免责声明直接推送。 */
    private String streamLowConfidenceDigest(List<ChatResponse.SourceItem> sources, Consumer<StreamEvent> sink) {
        StringBuilder fb = new StringBuilder(LOW_DISCLAIMER);
        for (int i = 0; i < Math.min(LOW_DIGEST_TOPN, sources.size()); i++) {
            var src = sources.get(i);
            String truncated = src.getContent().length() > 300 ? src.getContent().substring(0, 300) + "..." : src.getContent();
            fb.append("**[来源").append(src.getIndex()).append("]** 《").append(src.getSource()).append("》\n")
                    .append(truncated).append("\n\n");
        }
        fb.append(FALLBACK_ANSWER);
        String out = fb.toString();
        sink.accept(new StreamEvent("token", null, out));
        return out;
    }

    /** 闲聊意图的简短回复，LLM 失败时返回固定欢迎语兜底。 */
    private String chatReply(String question) {
        try {
            return callWithRetry(
                    () -> chatModel.chat(List.of(SystemMessage.from(CHAT_REPLY_PROMPT), UserMessage.from(question))).aiMessage().text(),
                    "LLM 闲聊回复");
        } catch (Exception e) {
            log.warn("LLM 闲聊回复最终失败: {}", e.getMessage());
            return "你好！我是校园事务咨询助手，有什么可以帮你的吗？";
        }
    }

    /** 检索分数不理想时让 LLM 变换关键词生成新检索词（只输出检索词），失败时退回原查询。 */
    private String retryKeyword(String lastQuery, double bestScore) {
        try {
            return callWithRetry(
                    () -> chatModel.chat(List.of(
                            SystemMessage.from("你是检索关键词优化助手。"),
                            UserMessage.from("你第一次用 '" + lastQuery + "' 检索，最高匹配度只有 " +
                                    String.format("%.2f", bestScore) + "。请变换关键词生成一个新的检索词。只输出检索词。")
                    )).aiMessage().text().trim().replaceAll("[\"']", ""),
                    "LLM 关键词改写");
        } catch (Exception e) {
            log.warn("LLM 关键词改写最终失败: {}", e.getMessage());
            return lastQuery;
        }
    }

    // ── 重排序 ───────────────────────────────────────────────────────────────────

    /**
     * 重排序：cross-encoder 语义分 + 关键词覆盖率组合排序，取 topK。
     * combined = -keywordScore*10 + rerankScore*0.1（升序排，越小越好）：
     * 关键词命中权重远高于语义分，因为 cross-encoder 对中文短句区分度有限，
     * 命中用户原词的片段更可能是正确答案；rerankScore 归一化后在 [-1,0]（越低越好），
     * 乘 0.1 缩放到次要项。
     */
    private List<Ranked> rerankWithScores(String question, List<HybridRetriever.Hit> hits, int topK) {
        if (hits.isEmpty()) return List.of();
        List<dev.langchain4j.data.segment.TextSegment> segments = new ArrayList<>(hits.size());
        for (HybridRetriever.Hit h : hits) segments.add(dev.langchain4j.data.segment.TextSegment.from(h.content()));
        List<Double> rerankerScores = scoringModel.scoreAll(segments, question).content();
        Set<String> queryKeywords = extractKeywords(question);

        List<Ranked> ranked = new ArrayList<>();
        for (int i = 0; i < hits.size(); i++) {
            double rerankScore = rerankerScores.get(i);
            double keywordScore = computeKeywordScore(hits.get(i).content(), queryKeywords);
            ranked.add(new Ranked(hits.get(i), -keywordScore * 10.0 + rerankScore * 0.1, rerankScore, keywordScore));
        }
        ranked.sort(Comparator.comparingDouble(Ranked::combined));
        return ranked.subList(0, Math.min(topK, ranked.size()));
    }

    /**
     * 提取查询关键词：先去掉疑问虚词（怎么办/如何/申请等），再按 2-4 字滑动 n-gram 切中文词。
     * 不依赖分词器，n-gram 虽有噪声但能覆盖未登录词（如"综测""第二课堂"）。
     */
    private Set<String> extractKeywords(String query) {
        Set<String> keywords = new HashSet<>();
        String cleaned = query.replaceAll("怎么办理|怎么办|怎么|如何|怎样|流程|请问|申请|需要|是否|可以|有什么|多少|哪些|一个|这个|那个|什么|哪里|为什么", " ");
        String chineseOnly = cleaned.replaceAll("[^\\u4e00-\\u9fff]", " ").trim();
        for (int n = 2; n <= 4; n++)
            for (int i = 0; i <= chineseOnly.length() - n; i++) {
                String gram = chineseOnly.substring(i, i + n).trim();
                if (gram.length() == n) keywords.add(gram);
            }
        return keywords;
    }

    /** 关键词覆盖率 = 片段中命中的关键词数 / 总关键词数，取值 [0,1]。 */
    private double computeKeywordScore(String content, Set<String> keywords) {
        if (keywords.isEmpty() || content == null) return 0.0;
        int matches = 0;
        for (String kw : keywords) if (content.contains(kw)) matches++;
        return (double) matches / keywords.size();
    }

    // ── 内部类型 ─────────────────────────────────────────────────────────────────

    /** 重排序中间结果：命中片段 + 组合分 + 两个分项分（供日志排查排序问题）。 */
    private record Ranked(HybridRetriever.Hit hit, double combined, double rerankScore, double keywordScore) {}

    /** 一次问答流程的可变状态，在各流水线阶段间传递，避免方法参数膨胀。 */
    private static class State {
        final String question;
        final List<ChatMessage> history;
        final Long sessionId;
        String intent;
        List<String> queries;
        List<HybridRetriever.Hit> searchResults;
        double bestScore;
        int retryCount;
        String confidence;
        /** 重排序模型缺失（NoOp 恒等分）时为 true，分级与输出需按降级口径处理（P1-12）。 */
        boolean rerankDegraded;
        /** 零引用标记：回答未引用任何来源时不展示"参考来源"列表，避免无关分块冒充依据。 */
        boolean dropSources;
        /** 已有 token 逐条推送过（真流式）；否则结束时补发一次完整 token（P0-1）。 */
        boolean streamedTokens;

        State(String q, List<ChatMessage> h, Long sessionId) {
            this.question = q; this.history = h; this.sessionId = sessionId;
        }
    }

    /**
     * SSE 流式事件。type 取值：stage（进度提示）/ token（回答文本）/ sources（来源）/ correction（引用修正）/ done（结束）。
     *
     * @param type    事件类型
     * @param stage   阶段 code（仅 stage 类型使用）
     * @param payload 事件载荷，类型随 type 变化
     */
    public record StreamEvent(String type, String stage, Object payload) {}
}
