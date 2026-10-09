package com.campus.forum.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/** 帖子评论：通过 parentId 表达楼中楼关系，children 用于返回评论树 */
@Data
@TableName("comment")
public class Comment {

    /** 主键 ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属帖子 ID */
    private Long postId;

    /** 评论作者用户 ID */
    private Long userId;

    /** 父评论 ID，0 表示一级评论 */
    private Long parentId;

    /** 评论内容 */
    private String content;

    /** 图片地址，JSON 数组字符串 */
    private String images;

    /** 点赞数 */
    private Integer likeCount;

    /** 0-已删除 1-正常 2-机审拦截 3-待人审 4-人审违规（仅 status=1 对读者可见） */
    private Integer status;

    /** 当前登录用户是否已点赞（非表字段，查询时回填） */
    @TableField(exist = false)
    private Boolean liked;

    /** 子回复列表（非表字段，构建评论树时填充） */
    @TableField(exist = false)
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private List<Comment> children;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
