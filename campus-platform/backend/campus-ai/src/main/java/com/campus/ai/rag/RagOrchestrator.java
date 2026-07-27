package com.campus.ai.rag;

import com.campus.ai.config.AiProperties;
import com.campus.ai.chat.dto.ChatRequest;
import com.campus.ai.chat.dto.ChatResponse;
import com.campus.ai.knowledge.model.SourceItem;
import com.campus.ai.rag.retrieval.HybridRetriever;
import dev.langchain4j.data.message.*;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.scoring.ScoringModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.function.Consumer;

/**
 * In-process replacement for the previous Python LangGraph pipeline. Drives the same
 * intent → rewrite → retrieve → rerank → retry → generate / fallback flow as the
 * Python service, but expressed as a plain while-loop on a {@link State} record (Java
 * 17 has no LangGraph port).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RagOrchestrator {

    private static final int MAX_RETRIES = 1;
    private static final int HISTORY_WINDOW = 10;
    private static final int RERANK_TOPK = 5;

    private static final String SYSTEM_PROMPT = """
            你是校园事务咨询助手。请根据下方"参考信息"回答学生问题，回答中用 [来源X] 标注引用。
            规则：
            1. 优先依据参考信息回答；参考信息不足时礼貌说明并给出建议联系部门。
            2. 不要编造参考信息中没有的制度、流程、数字。
            3. 引用编号必须与参考信息中 [来源X] 一一对应。
            4. 回答前先用一句简短寒暄或确认，再正式回答；保持简洁。""";

    private static final String CONTEXT_TEMPLATE = """
            参考信息：
            {context}

            请按要求回答学生问题。""";

    private static final String CHAT_REPLY_PROMPT = """
            你是校园事务咨询助手。用户在和你闲聊，请简短友好地回复，然后引导用户提出校园相关的问题。回复不超过两句话。""";

    private static final String REJECT_ANSWER = "抱歉，我只能回答校园事务相关的问题（如规章制度、课程考试、奖学金、休学等）。如果你有校园相关的问题，随时可以问我！";

    private static final String FALLBACK_ANSWER = "同学你好，在《学生手册》中未查询到相关规定。该问题可能涉及教务处具体业务，建议您联系教务处或学生事务中心咨询。";

    public static final Map<String, String> STAGE_MESSAGES = Map.of(
            "classify_done", "正在分析问题意图...",
            "rewrite_done", "正在优化搜索词...",
            "retrieve_done", "正在检索相关信息...",
            "rerank_done", "正在排序匹配结果...",
            "retry_done", "正在尝试其他关键词...",
            "generate_done", "正在整理回答...",
            "chat_reply_done", "正在回复...",
            "reject_reply_done", "正在回复...",
            "fallback_done", "正在整理回答...");

    private final ChatModel chatModel;
    private final IntentClassifier intentClassifier;
    private final QueryRewriter queryRewriter;
    private final HybridRetriever retriever;
    private final ScoringModel scoringModel;   // nullable — first request triggers model load
    private final AiProperties props;

    public ChatResponse run(ChatRequest request) {
        return run(request, stage -> { /* no-op for sync */ });
    }

    public ChatResponse run(ChatRequest request, Consumer<StreamEvent> sink) {
        State s = new State(request.getQuestion(), request.getHistory());

        s.intent = intentClassifier.classify(s.question);
        sink.accept(new StreamEvent("stage", "classify_done", STAGE_MESSAGES.get("classify_done")));

        switch (s.intent) {
            case "chat" -> {
                String reply = chatReply(s.question);
                ChatResponse r = new ChatResponse();
                r.setAnswer(reply);
                r.setSources(List.of());
                sink.accept(new StreamEvent("done", null, null));
                return r;
            }
            case "reject" -> {
                ChatResponse r = new ChatResponse();
                r.setAnswer(REJECT_ANSWER);
                r.setSources(List.of());
                sink.accept(new StreamEvent("done", null, null));
                return r;
            }
            default -> {
                // rag
            }
        }

        s.queries = queryRewriter.rewrite(s.question, s.history);
        sink.accept(new StreamEvent("stage", "rewrite_done", STAGE_MESSAGES.get("rewrite_done")));

        s.searchResults = retriever.searchMulti(s.queries, props.getVector().getRetrieveTopk());
        s.bestScore = s.searchResults.isEmpty() ? 999d : s.searchResults.get(0).score();
        sink.accept(new StreamEvent("stage", "retrieve_done", STAGE_MESSAGES.get("retrieve_done")));

        s.searchResults = rerank(s.question, s.searchResults, RERANK_TOPK);
        s.bestScore = s.searchResults.isEmpty() ? 999d : s.searchResults.get(0).score();
        sink.accept(new StreamEvent("stage", "rerank_done", STAGE_MESSAGES.get("rerank_done")));

        double threshold = props.getKnowledge().getScoreThreshold();
        while (s.bestScore > threshold && s.retryCount < MAX_RETRIES) {
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
            s.searchResults = new ArrayList<>(merged.values());
            s.searchResults.sort(Comparator.comparingDouble(HybridRetriever.Hit::score));
            if (s.searchResults.size() > props.getVector().getRetrieveTopk()) {
                s.searchResults = s.searchResults.subList(0, props.getVector().getRetrieveTopk());
            }
            s.bestScore = s.searchResults.isEmpty() ? 999d : s.searchResults.get(0).score();
            sink.accept(new StreamEvent("stage", "retry_done", STAGE_MESSAGES.get("retry_done")));
        }

        if (s.searchResults.isEmpty()) {
            ChatResponse r = new ChatResponse();
            r.setAnswer(FALLBACK_ANSWER);
            r.setSources(List.of());
            sink.accept(new StreamEvent("done", null, null));
            return r;
        }

        List<com.campus.ai.chat.dto.ChatResponse.SourceItem> sources = new ArrayList<>();
        int limit = Math.min(5, s.searchResults.size());
        for (int i = 0; i < limit; i++) {
            HybridRetriever.Hit h = s.searchResults.get(i);
            sources.add(com.campus.ai.chat.dto.ChatResponse.SourceItem.builder()
                    .index(i + 1)
                    .source(h.source())
                    .chunkIndex(h.chunkIndex())
                    .content(h.content())
                    .score(h.score())
                    .build());
        }
        sink.accept(new StreamEvent("sources", null, sources));

        String answer = generate(s, sources);
        sink.accept(new StreamEvent("stage", "generate_done", STAGE_MESSAGES.get("generate_done")));

        ChatResponse r = new ChatResponse();
        r.setAnswer(answer);
        r.setSources(sources);
        sink.accept(new StreamEvent("done", null, null));
        return r;
    }

    private List<HybridRetriever.Hit> rerank(String question, List<HybridRetriever.Hit> hits, int topK) {
        if (hits.isEmpty()) return hits;
        List<dev.langchain4j.data.segment.TextSegment> segments = new ArrayList<>(hits.size());
        for (HybridRetriever.Hit h : hits) segments.add(dev.langchain4j.data.segment.TextSegment.from(h.content()));
        List<Double> scores = scoringModel.scoreAll(segments, question).content();
        List<Ranked> ranked = new ArrayList<>();
        for (int i = 0; i < hits.size(); i++) {
            ranked.add(new Ranked(hits.get(i), scores.get(i)));
        }
        ranked.sort(Comparator.comparingDouble(Ranked::score));
        List<HybridRetriever.Hit> out = new ArrayList<>();
        for (int i = 0; i < Math.min(topK, ranked.size()); i++) out.add(ranked.get(i).hit);
        return out;
    }

    private String chatReply(String question) {
        try {
            return chatModel.chat(List.of(
                    SystemMessage.from(CHAT_REPLY_PROMPT),
                    UserMessage.from(question)
            )).aiMessage().text();
        } catch (Exception e) {
            return "你好！我是校园事务咨询助手，有什么可以帮你的吗？";
        }
    }

    private String retryKeyword(String lastQuery, double bestScore) {
        try {
            String prompt = "你第一次用 '" + lastQuery + "' 检索，最高匹配度只有 " +
                    String.format("%.2f", bestScore) + "（L2距离，越小越相似）。" +
                    "请变换关键词或换一种表述方式，生成一个新的检索词。只输出新的检索词，不要解释。";
            String r = chatModel.chat(List.of(
                    SystemMessage.from("你是检索关键词优化助手。"),
                    UserMessage.from(prompt)
            )).aiMessage().text().trim();
            return r.replaceAll("[\"']", "").trim();
        } catch (Exception e) {
            return lastQuery;
        }
    }

    private String generate(State s, List<com.campus.ai.chat.dto.ChatResponse.SourceItem> sources) {
        StringBuilder context = new StringBuilder();
        for (var src : sources) {
            context.append("[来源").append(src.getIndex()).append("] 来自《").append(src.getSource()).append("》:\n")
                    .append(src.getContent()).append("\n\n---\n\n");
        }
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(SystemMessage.from(SYSTEM_PROMPT));
        if (s.history != null) {
            int from = Math.max(0, s.history.size() - HISTORY_WINDOW);
            for (int i = from; i < s.history.size(); i++) {
                ChatRequest.HistoryItem h = s.history.get(i);
                if (h == null || h.getContent() == null) continue;
                if ("user".equalsIgnoreCase(h.getRole())) {
                    messages.add(UserMessage.from(h.getContent()));
                } else if ("assistant".equalsIgnoreCase(h.getRole())) {
                    messages.add(AiMessage.from(h.getContent()));
                }
            }
        }
        String userContent = CONTEXT_TEMPLATE.replace("{context}", context.toString()) +
                "\n\n学生问题: " + s.question;
        messages.add(UserMessage.from(userContent));
        try {
            return chatModel.chat(messages).aiMessage().text();
        } catch (Exception e) {
            log.error("LLM generation failed: {}", e.getMessage());
            StringBuilder fb = new StringBuilder("根据知识库检索，以下是相关信息：\n\n");
            for (var src : sources) {
                String content = src.getContent();
                String truncated = content.length() > 300 ? content.substring(0, 300) + "..." : content;
                fb.append("**[来源").append(src.getIndex()).append("]** 《").append(src.getSource()).append("》\n")
                        .append(truncated).append("\n\n");
            }
            return fb.toString();
        }
    }

    private record Ranked(HybridRetriever.Hit hit, double score) {}

    private static class State {
        final String question;
        final List<ChatRequest.HistoryItem> history;
        String intent;
        List<String> queries;
        List<HybridRetriever.Hit> searchResults;
        double bestScore;
        int retryCount;

        State(String q, List<ChatRequest.HistoryItem> h) {
            this.question = q;
            this.history = h;
        }
    }

    /** Lightweight event passed from the orchestrator to the SSE stream. */
    public record StreamEvent(String type, String stage, Object payload) {}
}
