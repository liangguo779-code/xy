package com.campus.ai.chat.dto;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class AiChatMessageVO {
    private Long id;
    private String role;
    private String content;
    private String sources;
    private LocalDateTime createTime;
}
