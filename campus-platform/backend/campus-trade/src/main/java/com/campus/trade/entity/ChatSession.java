package com.campus.trade.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("chat_session")
public class ChatSession {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long goodsId;

    private Long buyerId;

    private Long sellerId;

    private String lastMsg;

    private LocalDateTime lastTime;

    /** 0-关闭 1-活跃 */
    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
