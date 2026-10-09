package com.campus.app.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/** 站内公告，支持按类型分类与上下架状态管理 */
@Data
@TableName("announcement")
public class Announcement {
    /** 主键ID */
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 公告标题 */
    private String title;
    /** 公告正文内容 */
    private String content;
    /** 公告类型（如 notice-通知 announcement-公告） */
    private String type;
    /** 0-下架 1-上架 */
    private Integer status;
    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    /** 更新时间 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
