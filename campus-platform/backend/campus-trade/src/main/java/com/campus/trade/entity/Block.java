package com.campus.trade.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("user_block")
public class Block {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long blockedUserId;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
