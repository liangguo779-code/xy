package com.campus.trade.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AppealReviewReq {

    @NotBlank(message = "申诉理由不能为空")
    private String reason;
}
