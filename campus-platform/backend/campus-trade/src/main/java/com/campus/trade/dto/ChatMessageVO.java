package com.campus.trade.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ChatMessageVO {
    private Long id;
    private Long sessionId;
    private Long senderId;
    private String senderNickname;
    private String senderAvatar;
    private Integer msgType;
    private String content;
    private String extra;
    private Integer isRead;
    private LocalDateTime createTime;
}
