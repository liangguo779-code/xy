package com.campus.trade.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/** 用户拉黑记录，对应 user_block 表，被拉黑用户的消息/内容将被过滤 */
@Data
@TableName("user_block")
public class Block {
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 发起拉黑的用户 ID */
    private Long userId;
    /** 被拉黑的用户 ID */
    private Long blockedUserId;
    /** 拉黑时间，插入时自动填充 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
