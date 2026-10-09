package com.campus.ai.chat.dto;

import lombok.Data;
import java.time.LocalDateTime;

/** 聊天消息视图对象。 */
@Data
public class AiChatMessageVO {
    /** 消息 ID。 */
    private Long id;
    /** 角色：user / assistant。 */
    private String role;
    /** 消息正文。 */
    private String content;
    /** 来源引用 JSON（仅助手消息有值）。 */
    private String sources;
    /** 创建时间。 */
    private LocalDateTime createTime;
}
