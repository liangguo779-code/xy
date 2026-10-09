package com.campus.ai.chat.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

/** AI 对话请求体。 */
@Data
public class ChatRequest {

    /** 用户提问内容，不能为空。 */
    @NotBlank(message = "问题不能为空")
    private String question;

    /** 会话ID（可选，为空时自动创建） */
    private Long sessionId;

    /** 对话历史 */
    private List<HistoryItem> history;

    /** 历史消息条目。 */
    @Data
    public static class HistoryItem {
        /** 角色：user / assistant。 */
        private String role;
        /** 消息文本。 */
        private String content;
    }
}
