package com.campus.ai.chat.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/** 聊天会话表（ai_chat_session）实体。 */
@Data
@TableName("ai_chat_session")
public class AiChatSession {
    /** 主键。 */
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 归属用户 ID。 */
    private Long userId;
    /** 会话标题。 */
    private String title;
    /** 创建时间（插入时自动填充）。 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    /** 更新时间（插入/更新时自动填充，用于会话列表排序）。 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
