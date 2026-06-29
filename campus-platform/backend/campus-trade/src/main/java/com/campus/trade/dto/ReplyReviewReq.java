package com.campus.trade.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ReplyReviewReq {

    @NotBlank(message = "回复内容不能为空")
    private String reply;
}
