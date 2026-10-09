package com.campus.trade.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/** 用户关注关系，对应 follow 表，follower 关注 following */
@Data
@TableName("follow")
public class Follow {
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 关注者（粉丝）用户 ID */
    private Long followerId;
    /** 被关注者用户 ID */
    private Long followingId;
    /** 关注时间，插入时自动填充 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
