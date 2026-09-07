package com.campus.ai.assistant;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.service.V;
import dev.langchain4j.service.spring.AiService;
import dev.langchain4j.service.spring.AiServiceWiringMode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 查询改写器：将用户口语化的问题改写为 2-3 个更适合知识库检索的关键词/短句。
 *
 * <p>返回列表的第一个元素始终是原始问题（保证检索排序的稳定性）。
 * LLM 调用使用 LangChain4j AiServices，前/后处理在 Java 层完成。
 *
 * <p>如果没有这个文件：
 * <ul>
 *   <li>口语化问题（如"挂科了还能拿奖学金吗"）直接检索，召回率会很低</li>
 *   <li>无法从多个角度展开检索词，混合检索的向量和 BM25 通道都只能拿到原始问题</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QueryRewriter {

    /**
     * AiServices 接口，用于原始 LLM 调用。系统提示词固定；
     * 用户消息动态构建（包含格式化的历史上下文）。
     */
    @AiService(wiringMode = AiServiceWiringMode.EXPLICIT, chatModel = "chatModel")
    public interface LlmRewriter {
        @dev.langchain4j.service.SystemMessage("""
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
                大一挂科 奖学金""")
        @dev.langchain4j.service.UserMessage("{{userContent}}")
        String rewriteRaw(@V("userContent") String userContent);
    }

    private final LlmRewriter llmRewriter;

    public List<String> rewrite(String question, List<ChatMessage> history) {
        if (question == null || question.isBlank()) return List.of();
        // 对于简短、明确的问题（< 30 字），跳过改写。
        // LLM 改写经常会稀释精确查询（如"休学怎么办理"），加入无关扩展词，
        // 反而引入噪声 BM25 结果。
        if (question.length() <= 30) {
            return List.of(question);
        }
        try {
            String userContent = "Q: " + question;
            if (history != null && !history.isEmpty()) {
                StringBuilder ctx = new StringBuilder("\n最近对话：\n");
                int from = Math.max(0, history.size() - 5);
                for (int i = from; i < history.size(); i++) {
                    ChatMessage h = history.get(i);
                    if (h instanceof UserMessage um) {
                        String t = um.singleText();
                        if (t != null && !t.isBlank()) ctx.append("user: ").append(t).append("\n");
                    } else if (h instanceof AiMessage am) {
                        String t = am.text();
                        if (t != null && !t.isBlank()) ctx.append("assistant: ").append(t).append("\n");
                    }
                }
                userContent = userContent + ctx;
            }
            String raw = llmRewriter.rewriteRaw(userContent);
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
