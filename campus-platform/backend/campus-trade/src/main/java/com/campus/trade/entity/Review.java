package com.campus.trade.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/** 订单评价，对应 review 表，买家对卖家的评价及被评价者申诉/回复 */
@Data
@TableName("review")
public class Review {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关联的订单 ID */
    private Long orderId;

    /** 评价者（买家）用户 ID */
    private Long reviewerId;

    /** 被评价者（卖家）用户 ID */
    private Long targetId;

    /** 评分 1-5 */
    private Integer rating;

    /** 评价文字内容 */
    private String content;

    /** 标签JSON数组 ["态度好","描述准确","发货快"] */
    private String tags;

    /** 1-正常 0-已屏蔽 */
    private Integer status;

    /** 申诉理由 */
    private String appealReason;

    /** 0-无申诉 1-申诉中 2-申诉通过 3-申诉驳回 */
    private Integer appealStatus;

    /** 申诉提交时间 */
    private LocalDateTime appealTime;

    /** 被评价者回复 */
    private String reply;

    /** 回复时间 */
    private LocalDateTime replyTime;

    /** 评价时间，插入时自动填充 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /** 更新时间，插入/更新时自动填充 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
