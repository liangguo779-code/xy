package com.campus.trade.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** 评价申诉请求，卖家对差评发起申诉 */
@Data
public class AppealReviewReq {

    /** 申诉理由 */
    @NotBlank(message = "申诉理由不能为空")
    private String reason;
}
