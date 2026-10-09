package com.campus.ai.moderation;

import com.campus.common.moderation.ContentAuditClient;
import com.campus.common.moderation.ModerationProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * AI 审核 API 的 LLM 实现：复用现有 openai-compatible 对话模型做结构化内容裁决，
 * 输出 JSON（verdict/confidence/categories/severity/reason）。
 * 未来接入第三方内容安全 API 时另写 {@link ContentAuditClient} 实现即可替换。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LlmContentAuditClient implements ContentAuditClient {

    private static final String AUDIT_SYSTEM_PROMPT = """
            你是高校社区的内容安全审核员。请对用户发布的评论做违规判定。
            类目体系：色情、政治、暴恐违禁、广告、谩骂、不良价值观、谣言、隐私侵犯、其他。
            严重程度：1-轻微 2-中等 3-严重。
            只输出 JSON，不要输出任何其他文字，格式如下：
            {"verdict":0或1或2, "confidence":0到1的小数, "categories":["类目"], "severity":1到3, "reason":"判定理由"}
            其中 verdict：0-合规 1-违规 2-不确定（证据不足/边界情形）。
            """;

    private final ChatModel chatModel;
    private final ModerationProperties props;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public AiVerdict audit(String content, String reason) {
        String userPrompt = "举报理由：" + (reason == null || reason.isBlank() ? "（无）" : reason)
                + "\n待审内容：\n" + content;
        String raw = chatModel.chat(List.of(
                SystemMessage.from(AUDIT_SYSTEM_PROMPT),
                UserMessage.from(userPrompt))).aiMessage().text();
        return parse(raw);
    }

    /** 解析 LLM 输出的 JSON 裁决（容忍 ```json 代码栅栏等包裹）。 */
    AiVerdict parse(String raw) {
        try {
            String s = raw.trim();
            if (s.startsWith("```")) {
                s = s.replaceFirst("^```(?:json)?\\s*", "").replaceFirst("\\s*```$", "");
            }
            int start = s.indexOf('{');
            int end = s.lastIndexOf('}');
            if (start >= 0 && end > start) s = s.substring(start, end + 1);
            JsonNode n = objectMapper.readTree(s);
            int verdict = n.path("verdict").asInt(2);
            if (verdict < 0 || verdict > 2) verdict = 2;
            double confidence = n.path("confidence").asDouble(0);
            List<String> categories = new ArrayList<>();
            for (JsonNode c : n.path("categories")) {
                if (!c.asText().isBlank()) categories.add(c.asText());
            }
            int severity = Math.max(1, Math.min(3, n.path("severity").asInt(1)));
            String reasonText = n.path("reason").asText("");
            return new AiVerdict(verdict, confidence, categories, severity, reasonText);
        } catch (Exception e) {
            log.warn("AI 裁决 JSON 解析失败，按不确定处理: {} / raw={}", e.getMessage(), raw);
            return AiVerdict.uncertain("裁决解析失败: " + e.getMessage());
        }
    }
}
