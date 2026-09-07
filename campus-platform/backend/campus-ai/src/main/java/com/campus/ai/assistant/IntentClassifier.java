package com.campus.ai.assistant;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import dev.langchain4j.service.spring.AiService;
import dev.langchain4j.service.spring.AiServiceWiringMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 意图分类器：将用户问题分为 {@code chat}（闲聊）、{@code rag}（校园咨询）或 {@code reject}（无关）。
 *
 * <p>使用轻量级提示词完成分类。出错时默认返回 {@code rag}，确保用户仍能得到回答。
 * 基于 LangChain4j AiServices 声明式接口，无需手动调用 {@code chatModel.chat()}。
 *
 * <p>如果没有这个文件：
 * <ul>
 *   <li>"你好""你是谁"等闲聊问题会走完整的检索+LLM流程，浪费资源且体验差</li>
 *   <li>"帮我写代码""翻译文章"等无关问题也会进入 RAG，产生无意义的回答</li>
 *   <li>RagOrchestrator 无法在入口处快速分流，所有请求都要走完检索流水线</li>
 * </ul>
 */
@Service
@AiService(wiringMode = AiServiceWiringMode.EXPLICIT, chatModel = "chatModel")
public interface IntentClassifier {

    Logger log = LoggerFactory.getLogger(IntentClassifier.class);

    @SystemMessage("""
            你是校园事务咨询系统的意图分类器。根据用户的问题判断其意图类型。
            可选的意图:
            - chat: 闲聊问候（"你好"、"你是谁"等不涉及具体校园事务的问题）
            - rag: 校园事务咨询（涉及规章制度、课程考试、奖学金、休学、食堂、宿舍等具体校园问题）
            - reject: 与校园完全无关的请求（"写代码"、"翻译文章"等）
            只输出一个词: chat 或 rag 或 reject。不要解释。""")
    @UserMessage("用户: {{question}}")
    String classifyRaw(@V("question") String question);

    default String classify(String question) {
        try {
            String raw = classifyRaw(question).trim().toLowerCase();
            if (raw.contains("chat")) return "chat";
            if (raw.contains("reject")) return "reject";
            return "rag";
        } catch (Exception e) {
            log.warn("Intent classification failed, defaulting to rag: {}", e.getMessage());
            return "rag";
        }
    }
}
