package com.campus.trade.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/** 帖子评论，对应 comment 表，支持楼中楼（父子）两级回复 */
@Data
@TableName("comment")
public class Comment {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属帖子 ID */
    private Long postId;

    /** 评论者用户 ID */
    private Long userId;

    /** 父评论 ID，顶级评论为 null */
    private Long parentId;

    /** 评论文字内容 */
    private String content;

    /** 评论图片（JSON 数组，存图片 URL 列表） */
    private String images;

    /** 点赞数 */
    private Integer likeCount;

    /** 状态：0-删除/隐藏 1-正常 */
    private Integer status;

    /** 当前登录用户是否已点赞，非数据库字段，查询时填充 */
    @TableField(exist = false)
    private Boolean liked;

    /** 子回复列表，非数据库字段，查询时递归填充 */
    @TableField(exist = false)
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private List<Comment> children;

    /** 评论时间，插入时自动填充 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
