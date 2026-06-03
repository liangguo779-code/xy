package com.campus.trade.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class SendMessageReq {

    @NotNull(message = "会话ID不能为空")
    private Long sessionId;

    /** 0-文本 1-图片 3-快捷操作 */
    private Integer msgType = 0;

    @NotBlank(message = "消息内容不能为空")
    private String content;

    /** 扩展数据 */
    private String extra;
}
