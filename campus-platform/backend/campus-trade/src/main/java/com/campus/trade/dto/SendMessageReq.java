package com.campus.trade.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 发送聊天消息请求，REST 与 WebSocket 通道共用 */
@Data
public class SendMessageReq {

    /** 会话ID */
    @NotNull(message = "会话ID不能为空")
    private Long sessionId;

    /** 0-文本 1-图片 3-快捷操作 */
    private Integer msgType = 0;

    /** 消息正文（图片时为图片URL） */
    @NotBlank(message = "消息内容不能为空")
    private String content;

    /** 扩展数据 */
    private String extra;
}
