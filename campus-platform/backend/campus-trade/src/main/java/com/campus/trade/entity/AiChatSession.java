package com.campus.trade.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/** AI 智能助手对话会话，对应 ai_chat_session 表，一次会话包含多条消息 */
@Data
@TableName("ai_chat_session")
public class AiChatSession {
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 会话所属用户 ID */
    private Long userId;
    /** 会话标题，一般取首条提问的摘要 */
    private String title;
    /** 创建时间，插入时自动填充 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    /** 更新时间，插入/更新时自动填充 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
