package com.campus.ai.rag;

import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Classifies a user question into {@code chat}, {@code rag}, or {@code reject} using a
 * lightweight prompt. Port of {@code rag.graph.node_classify} from the previous Python
 * service. On any error or empty key, defaults to {@code rag} so the user still gets a
 * useful answer.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IntentClassifier {

    private static final String SYSTEM_PROMPT = """
            你是校园事务咨询系统的意图分类器。根据用户的问题判断其意图类型。
            可选的意图:
            - chat: 闲聊问候（"你好"、"你是谁"等不涉及具体校园事务的问题）
            - rag: 校园事务咨询（涉及规章制度、课程考试、奖学金、休学、食堂、宿舍等具体校园问题）
            - reject: 与校园完全无关的请求（"写代码"、"翻译文章"等）
            只输出一个词: chat 或 rag 或 reject。不要解释。""";

    private final ChatModel chatModel;

    public String classify(String question) {
        try {
            String raw = chatModel.chat(List.of(
                    SystemMessage.from(SYSTEM_PROMPT),
                    UserMessage.from("用户: " + question)
            )).aiMessage().text().trim().toLowerCase();
            if (raw.contains("chat")) return "chat";
            if (raw.contains("reject")) return "reject";
            return "rag";
        } catch (Exception e) {
            log.warn("Intent classification failed, defaulting to rag: {}", e.getMessage());
            return "rag";
        }
    }
}
