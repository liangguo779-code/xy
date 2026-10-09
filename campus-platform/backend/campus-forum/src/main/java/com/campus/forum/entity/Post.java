package com.campus.forum.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/** 论坛帖子：帖子内容与浏览/点赞/评论计数，status 控制可见性 */
@Data
@TableName("post")
public class Post {

    /** 主键 ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 作者用户 ID */
    private Long userId;

    /** 帖子标题 */
    private String title;

    /** 帖子正文 */
    private String content;

    /** 帖子分类 */
    private String category;

    /** 图片地址，JSON 数组字符串 */
    private String images;

    /** 浏览数 */
    private Integer viewCount;

    /** 点赞数 */
    private Integer likeCount;

    /** 评论数 */
    private Integer commentCount;

    /** 0-未置顶 1-置顶 */
    private Integer isTop;

    /** 0-已删除/隐藏 1-正常 */
    private Integer status;

    /** 当前登录用户是否已点赞（非表字段，查询详情时回填） */
    @TableField(exist = false)
    private Boolean liked;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /** 更新时间 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
