package com.campus.trade.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/** AI 智能助手对话消息，对应 ai_chat_message 表，记录单条提问/回答内容 */
@Data
@TableName("ai_chat_message")
public class AiChatMessage {
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 所属 AI 会话 ID，关联 ai_chat_session.id */
    private Long sessionId;
    /** 消息角色：user-用户提问 assistant-AI 回答 */
    private String role;
    /** 消息正文内容 */
    private String content;
    /** RAG 引用来源（JSON 格式），记录回答所依据的知识库片段 */
    private String sources;
    /** 创建时间，插入时自动填充 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
