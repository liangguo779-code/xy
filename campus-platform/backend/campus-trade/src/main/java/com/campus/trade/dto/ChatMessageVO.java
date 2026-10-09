package com.campus.trade.dto;

import lombok.Data;

import java.time.LocalDateTime;

/** 聊天消息视图对象，返回给前端时附带发送人昵称/头像等冗余信息 */
@Data
public class ChatMessageVO {
    /** 消息ID */
    private Long id;
    /** 所属会话ID */
    private Long sessionId;
    /** 发送者用户ID */
    private Long senderId;
    /** 发送者昵称（查询时冗余填充） */
    private String senderNickname;
    /** 发送者头像URL（查询时冗余填充） */
    private String senderAvatar;
    /** 消息类型：0-文本 1-图片（预留扩展） */
    private Integer msgType;
    /** 消息正文（图片时为图片URL） */
    private String content;
    /** 附加数据JSON（如商品快照、卡片等） */
    private String extra;
    /** 是否已读：0-未读 1-已读 */
    private Integer isRead;
    /** 撤回时间，非空表示消息已被撤回 */
    private LocalDateTime recallTime;
    /** 发送时间 */
    private LocalDateTime createTime;
}
