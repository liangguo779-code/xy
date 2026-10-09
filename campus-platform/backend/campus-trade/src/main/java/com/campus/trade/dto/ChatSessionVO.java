package com.campus.trade.dto;

import lombok.Data;

import java.time.LocalDateTime;

/** 聊天会话视图对象，会话列表展示用，附带商品与对方用户的摘要信息 */
@Data
public class ChatSessionVO {
    /** 会话ID */
    private Long id;
    /** 会话关联的商品ID */
    private Long goodsId;
    /** 商品标题（冗余填充） */
    private String goodsTitle;
    /** 商品封面图URL（冗余填充） */
    private String goodsImage;
    /** 对方（会话中另一方）用户ID */
    private Long otherUserId;
    /** 对方昵称（冗余填充） */
    private String otherNickname;
    /** 对方头像URL（冗余填充） */
    private String otherAvatar;
    /** 最后一条消息摘要 */
    private String lastMsg;
    /** 最后一条消息时间，用于会话排序 */
    private LocalDateTime lastTime;
    /** 当前用户在该会话的未读条数 */
    private Integer unreadCount;
}
