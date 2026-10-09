package com.campus.forum.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/** 帖子收藏记录：用户与帖子的收藏关联，同一用户对同一帖子最多一条 */
@Data
@TableName("post_favorite")
public class PostFavorite {

    /** 主键 ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 收藏人用户 ID */
    private Long userId;

    /** 被收藏的帖子 ID */
    private Long postId;

    /** 收藏时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
