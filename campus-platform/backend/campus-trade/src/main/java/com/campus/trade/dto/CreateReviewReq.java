package com.campus.trade.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CreateReviewReq {

    @NotNull(message = "订单ID不能为空")
    private Long orderId;

    @Min(1) @Max(5)
    @NotNull(message = "评分不能为空")
    private Integer rating;

    private String content;

    /** 评价标签JSON数组 */
    private String tags;
}
