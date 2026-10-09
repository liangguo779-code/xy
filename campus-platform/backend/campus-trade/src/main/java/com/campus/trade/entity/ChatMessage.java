package com.campus.trade.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/** 聊天消息，对应 chat_message 表，买卖双方私聊的单条消息 */
@Data
@TableName("chat_message")
public class ChatMessage {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属会话 ID，关联 chat_session.id */
    private Long sessionId;

    /** 发送者用户 ID */
    private Long senderId;

    /** 0-文本 1-图片 2-系统通知 3-快捷操作 */
    private Integer msgType;

    /** 消息内容：文本原文或图片 URL */
    private String content;

    /** 附加数据（JSON），如图片尺寸、快捷操作参数等 */
    private String extra;

    /** 是否已读：0-未读 1-已读 */
    private Integer isRead;

    /** 撤回时间（null表示未撤回） */
    private LocalDateTime recallTime;

    /** 发送时间，插入时自动填充 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
