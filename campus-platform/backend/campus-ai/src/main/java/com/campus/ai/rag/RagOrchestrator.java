package com.campus.ai.rag;

import com.campus.ai.assistant.CampusRagAssistant;
import com.campus.ai.assistant.IntentClassifier;
import com.campus.ai.assistant.QueryRewriter;
import com.campus.ai.config.AiProperties;
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

    private static final int MAX_RETRIES = 1;
    private static final int HISTORY_WINDOW = 20;
    private static final int RERANK_TOPK = 10;
    private static final int LOW_DIGEST_TOPN = 3;

    /** LLM 调用最大重试次数（针对临时故障：5xx、网络超时、连接重置）。 */
    private static final int LLM_MAX_RETRIES = 2;
    /** LLM 重试基础等待时间（毫秒），指数退避：1s → 2s → 4s。 */
    private static final long LLM_RETRY_BASE_MS = 1000L;

    private static final String CHAT_REPLY_PROMPT = """
            你是校园事务咨询助手。用户在和你闲聊，请简短友好地回复，然后引导用户提出校园相关的问题。回复不超过两句话。""";

    private static final String REJECT_ANSWER = "抱歉，我只能回答校园事务相关的问题（如规章制度、课程考试、奖学金、休学等）。如果你有校园相关的问题，随时可以问我！";
    private static final String FALLBACK_ANSWER = "同学你好，在《学生手册》中未查询到相关规定。该问题可能涉及教务处具体业务，建议您联系教务处或学生事务中心咨询。";

    /** 置信度为"中"时，追加到检索上下文前面的提示语。 */
    private static final String MEDIUM_PROMPT_ADDENDUM = """
            【置信度提示】本次检索结果与问题匹配度一般。请严格依据参考信息回答；若参考信息无法确认，务必明确说"无法确定"，不要硬编内容，也不要引用未列出的来源编号。""";

    private static final String MEDIUM_DISCLAIMER = "（以下信息基于检索片段整理，建议联系教务处或学生事务中心二次确认）\n\n";
    private static final String LOW_DISCLAIMER = "在《学生手册》中未找到与该问题高度匹配的内容，以下片段相关性较低，仅供参考：\n\n";

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
    private final ScoringModel scoringModel;
    private final AiProperties props;
    private final MemoryStore memoryStore;
    private final CampusRagAssistant ragAssistant;

    // ── 公共入口 ─────────────────────────────────────────────────────────────────

    public ChatResponse run(ChatRequest request, Long sessionId, Consumer<StreamEvent> sink) {
        List<ChatMessage> history = memoryStore.getOrCreate(sessionId, HISTORY_WINDOW).messages();
        State s = new State(request.getQuestion(), history, sessionId);

        s.intent = intentClassifier.classify(s.question);
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
        s.queries = queryRewriter.rewrite(s.question, s.history);
        sink.accept(new StreamEvent("stage", "rewrite_done", STAGE_MESSAGES.get("rewrite_done")));

        s.searchResults = retriever.searchMulti(s.queries, props.getVector().getRetrieveTopk());
        log.info("检索完成: queries={}, 结果数={}", s.queries, s.searchResults.size());
        sink.accept(new StreamEvent("stage", "retrieve_done", STAGE_MESSAGES.get("retrieve_done")));

        List<Ranked> ranked = rerankWithScores(s.question, s.searchResults, RERANK_TOPK);
        s.searchResults = ranked.stream().map(Ranked::hit).toList();
        s.bestScore = ranked.isEmpty() ? ConfidenceGrader.NO_RESULT : ranked.get(0).rerankScore();
        s.confidence = ConfidenceGrader.grade(s.bestScore, props);
        log.info("置信度={}, topRerankScore={}", s.confidence, s.bestScore);
        sink.accept(new StreamEvent("stage", "rerank_done", STAGE_MESSAGES.get("rerank_done")));

        // 分数不理想时重试一次
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
            s.confidence = ConfidenceGrader.grade(s.bestScore, props);
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
        sink.accept(new StreamEvent("sources", null, sources));
        sink.accept(new StreamEvent("done", null, null));
        return buildResponse(answer, sources, s.confidence, sessionId);
    }

    // ── 生成回答 ─────────────────────────────────────────────────────────────────

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
            answer = callWithRetry(
                    () -> ragAssistant.chatWithContext(s.sessionId, context.toString(), s.question),
                    "LLM 生成回答");
        } catch (Exception e) {
            log.error("LLM 生成回答最终失败: {}", e.getMessage());
            answer = null;
        }

        if (answer != null && !answer.isEmpty()) {
            sink.accept(new StreamEvent("stage", "citation_validated", STAGE_MESSAGES.get("citation_validated")));
            return postProcessAnswer(answer, sources, s.confidence, sink);
        }

        log.warn("LLM returned empty/null answer, using fallback digest");
        return streamLowConfidenceDigest(sources, sink);
    }

    private String postProcessAnswer(String rawAnswer, List<ChatResponse.SourceItem> sources,
                                     String confidence, Consumer<StreamEvent> sink) {
        CitationValidator.Result v = CitationValidator.validate(rawAnswer, sources.size());
        if (CitationValidator.shouldReject(v.totalCitations(), v.fabricatedCount())) {
            log.warn("引用校验拒绝: total={}, fabricated={}", v.totalCitations(), v.fabricatedCount());
            emitCorrection(sink, v, true);
            return streamLowConfidenceDigest(sources, sink);
        }
        String finalAnswer = v.cleanedAnswer();
        if (v.fabricatedCount() > 0) {
            log.warn("引用校验剥离: fabricated={}", v.fabricatedIndices());
            emitCorrection(sink, v, false);
        }
        if (ConfidenceGrader.MEDIUM.equals(confidence) && !finalAnswer.startsWith("（")) {
            finalAnswer = MEDIUM_DISCLAIMER + finalAnswer;
        }
        return finalAnswer;
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

    private double computeKeywordScore(String content, Set<String> keywords) {
        if (keywords.isEmpty() || content == null) return 0.0;
        int matches = 0;
        for (String kw : keywords) if (content.contains(kw)) matches++;
        return (double) matches / keywords.size();
    }

    // ── 内部类型 ─────────────────────────────────────────────────────────────────

    private record Ranked(HybridRetriever.Hit hit, double combined, double rerankScore, double keywordScore) {}

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

        State(String q, List<ChatMessage> h, Long sessionId) {
            this.question = q; this.history = h; this.sessionId = sessionId;
        }
    }

    public record StreamEvent(String type, String stage, Object payload) {}
}
