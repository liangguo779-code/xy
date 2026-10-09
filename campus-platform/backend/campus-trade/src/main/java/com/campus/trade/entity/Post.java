package com.campus.trade.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/** 社区帖子，对应 post 表，校园动态/交流帖 */
@Data
@TableName("post")
public class Post {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 发帖用户 ID */
    private Long userId;

    /** 帖子标题 */
    private String title;

    /** 帖子正文内容 */
    private String content;

    /** 帖子分类（如 校园动态/失物招领/求助 等） */
    private String category;

    /** 帖子图片（JSON 数组，存图片 URL 列表） */
    private String images;

    /** 浏览数 */
    private Integer viewCount;

    /** 点赞数 */
    private Integer likeCount;

    /** 评论数 */
    private Integer commentCount;

    /** 是否置顶：0-否 1-是 */
    private Integer isTop;

    /** 状态：0-删除/隐藏 1-正常 */
    private Integer status;

    /** 当前登录用户是否已点赞，非数据库字段，查询时填充 */
    @TableField(exist = false)
    private Boolean liked;

    /** 发帖时间，插入时自动填充 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /** 更新时间，插入/更新时自动填充 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
