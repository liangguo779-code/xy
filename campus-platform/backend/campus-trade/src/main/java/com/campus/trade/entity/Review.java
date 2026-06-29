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

    /** 1-正常 0-已屏蔽 */
    private Integer status;

    /** 申诉理由 */
    private String appealReason;

    /** 0-无申诉 1-申诉中 2-申诉通过 3-申诉驳回 */
    private Integer appealStatus;

    private LocalDateTime appealTime;

    /** 被评价者回复 */
    private String reply;

    private LocalDateTime replyTime;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
