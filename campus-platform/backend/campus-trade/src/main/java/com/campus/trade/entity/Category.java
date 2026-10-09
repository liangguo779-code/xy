package com.campus.trade.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/** 商品分类，对应 category 表，支持父子两级分类树 */
@Data
@TableName("category")
public class Category {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 分类名称 */
    private String name;

    /** 分类图标（URL 或图标 key） */
    private String icon;

    /** 排序序号，值越小越靠前 */
    private Integer sortOrder;

    /** 父分类 ID，顶级分类为 0 或 null */
    private Long parentId;

    /** 状态：0-禁用 1-启用 */
    private Integer status;

    /** 创建时间，插入时自动填充 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
