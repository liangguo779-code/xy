package com.campus.trade.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("goods")
public class Goods {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String title;

    private String description;

    private BigDecimal price;

    private BigDecimal originalPrice;

    private Long categoryId;

    private String category;

    private String images;

    private String videoUrl;

    /** 成色: 全新/几乎全新/良好/一般 */
    @TableField("`condition`")
    private String condition;

    private String location;

    /** 0-出售 1-求购 */
    @TableField("`type`")
    private Integer type;

    /** 0-已上架 1-已下架 2-已售出 3-待审核 */
    private Integer status;

    /** 下架原因 */
    private String offReason;

    private Integer viewCount;

    private Integer wantCount;

    private Integer likeCount;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
