package com.campus.ai.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

@Data
public class ChatRequest {

    @NotBlank(message = "问题不能为空")
    private String question;

    /** 会话ID（可选，为空时自动创建） */
    private Long sessionId;

    /** 对话历史 */
    private List<HistoryItem> history;

    @Data
    public static class HistoryItem {
        private String role;
        private String content;
    }
}
