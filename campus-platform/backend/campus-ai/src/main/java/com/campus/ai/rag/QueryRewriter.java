package com.campus.ai.rag;

import com.campus.ai.chat.dto.ChatRequest;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Generates 2-3 alternate phrasings of the user's question for hybrid retrieval. Port of
 * {@code rag.rewrite.rewrite_query}. Returns a list whose first item is the original
 * question (kept for stable ranking in {@code hybridSearchMultiQuery}).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QueryRewriter {

    private static final String SYSTEM_PROMPT = """
            你是校园知识库检索词改写助手。
            任务：把用户口语化的问题改写为 2-3 个更适合在校务知识库中检索的关键词/短句。
            要求：
            1. 保留原意；不要引入无关信息
            2. 每行一个检索词
            3. 不要解释、不要编号、不要标点
            4. 第一个检索词保持原问题本身
            示例：
            Q: 请问如果我大一挂科了还能拿奖学金吗
            挂科 奖学金
            学业警告 奖学金评定
            大一挂科 奖学金""";

    private final ChatModel chatModel;

    public List<String> rewrite(String question, List<ChatRequest.HistoryItem> history) {
        if (question == null || question.isBlank()) return List.of();
        try {
            String userContent = "Q: " + question;
            if (history != null && !history.isEmpty()) {
                StringBuilder ctx = new StringBuilder("\n最近对话：\n");
                int from = Math.max(0, history.size() - 5);
                for (int i = from; i < history.size(); i++) {
                    ChatRequest.HistoryItem h = history.get(i);
                    if (h.getContent() != null && !h.getContent().isBlank()) {
                        ctx.append(h.getRole()).append(": ").append(h.getContent()).append("\n");
                    }
                }
                userContent = userContent + ctx;
            }
            String raw = chatModel.chat(List.of(
                    SystemMessage.from(SYSTEM_PROMPT),
                    UserMessage.from(userContent)
            )).aiMessage().text();
            List<String> out = new ArrayList<>();
            for (String line : raw.split("\\n")) {
                String t = line.trim().replaceAll("^\\d+[.、]\\s*", "").replaceAll("[\"']", "");
                if (!t.isEmpty()) out.add(t);
            }
            if (out.isEmpty()) out.add(question);
            if (!out.get(0).equals(question)) out.add(0, question);
            return out;
        } catch (Exception e) {
            log.warn("Query rewrite failed, returning original: {}", e.getMessage());
            return List.of(question);
        }
    }
}
