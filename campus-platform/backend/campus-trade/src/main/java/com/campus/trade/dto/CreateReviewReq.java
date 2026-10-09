package com.campus.trade.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 发布评价请求，评价对象由 orderId 唯一确定 */
@Data
public class CreateReviewReq {

    /** 订单ID */
    @NotNull(message = "订单ID不能为空")
    private Long orderId;

    /** 评分，1-5 星 */
    @Min(1) @Max(5)
    @NotNull(message = "评分不能为空")
    private Integer rating;

    /** 评价文字内容，可空 */
    private String content;

    /** 评价标签JSON数组 */
    private String tags;
}
