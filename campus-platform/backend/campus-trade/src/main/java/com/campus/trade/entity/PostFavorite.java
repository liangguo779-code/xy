package com.campus.trade.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/** 帖子收藏，对应 post_favorite 表，用户收藏的社区帖子 */
@Data
@TableName("post_favorite")
public class PostFavorite {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 收藏者用户 ID */
    private Long userId;

    /** 被收藏的帖子 ID */
    private Long postId;

    /** 收藏时间，插入时自动填充 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
