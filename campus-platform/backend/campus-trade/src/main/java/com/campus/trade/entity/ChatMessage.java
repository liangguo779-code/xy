package com.campus.trade.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("chat_message")
public class ChatMessage {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long sessionId;

    private Long senderId;

    /** 0-文本 1-图片 2-系统通知 3-快捷操作 */
    private Integer msgType;

    private String content;

    private String extra;

    private Integer isRead;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
