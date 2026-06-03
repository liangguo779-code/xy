package com.campus.ai.dto;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class AiChatSessionVO {
    private Long id;
    private String title;
    private String lastMessage;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
