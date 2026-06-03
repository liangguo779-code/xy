package com.campus.trade.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("review")
public class Review {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long orderId;

    private Long reviewerId;

    private Long targetId;

    /** 评分 1-5 */
    private Integer rating;

    private String content;

    /** 标签JSON数组 ["态度好","描述准确","发货快"] */
    private String tags;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
