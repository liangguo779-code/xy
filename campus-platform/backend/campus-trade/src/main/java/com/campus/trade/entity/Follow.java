package com.campus.trade.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("follow")
public class Follow {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long followerId;
    private Long followingId;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
