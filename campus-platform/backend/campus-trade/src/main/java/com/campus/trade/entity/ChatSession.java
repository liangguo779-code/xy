package com.campus.trade.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/** 聊天会话，对应 chat_session 表，围绕一件商品建立的买卖双方会话 */
@Data
@TableName("chat_session")
public class ChatSession {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 会话关联的商品 ID */
    private Long goodsId;

    /** 买家用户 ID */
    private Long buyerId;

    /** 卖家用户 ID */
    private Long sellerId;

    /** 最近一条消息摘要，用于会话列表展示 */
    private String lastMsg;

    /** 最近一条消息时间，用于会话列表排序 */
    private LocalDateTime lastTime;

    /** 0-关闭 1-活跃 */
    private Integer status;

    /** 创建时间，插入时自动填充 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
