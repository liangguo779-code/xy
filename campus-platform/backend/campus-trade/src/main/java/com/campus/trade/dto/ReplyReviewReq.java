package com.campus.trade.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** 评价回复请求，卖家对评价进行公开回复 */
@Data
public class ReplyReviewReq {

    /** 回复内容 */
    @NotBlank(message = "回复内容不能为空")
    private String reply;
}
