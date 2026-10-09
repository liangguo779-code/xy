package com.campus.ai.chat.dto;

import lombok.Data;
import java.time.LocalDateTime;

/** 聊天会话视图对象。 */
@Data
public class AiChatSessionVO {
    /** 会话 ID。 */
    private Long id;
    /** 会话标题（默认取首问前 30 字）。 */
    private String title;
    /** 最后一条消息的 50 字预览。 */
    private String lastMessage;
    /** 创建时间。 */
    private LocalDateTime createTime;
    /** 最后活跃时间。 */
    private LocalDateTime updateTime;
}
