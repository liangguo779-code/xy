package com.campus.ai.chat.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/** 聊天消息表（ai_chat_message）实体。 */
@Data
@TableName("ai_chat_message")
public class AiChatMessage {
    /** 主键。 */
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 所属会话 ID。 */
    private Long sessionId;
    /** 角色：user / assistant。 */
    private String role;
    /** 消息正文。 */
    private String content;
    /** 来源引用 JSON（仅助手消息有值）。 */
    private String sources;
    /** 创建时间（插入时自动填充）。 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
