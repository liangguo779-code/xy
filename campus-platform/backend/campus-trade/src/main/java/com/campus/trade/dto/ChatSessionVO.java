package com.campus.trade.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ChatSessionVO {
    private Long id;
    private Long goodsId;
    private String goodsTitle;
    private String goodsImage;
    private Long otherUserId;
    private String otherNickname;
    private String otherAvatar;
    private String lastMsg;
    private LocalDateTime lastTime;
    private Integer unreadCount;
}
