package com.campus.trade.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.campus.trade.dto.*;
import com.campus.trade.entity.ChatMessage;
import com.campus.trade.entity.ChatSession;

import java.util.List;

public interface ChatService extends IService<ChatMessage> {

    /**
     * 创建或获取聊天会话
     * @param userId 当前用户
     * @param goodsId 商品ID
     * @param otherUserId 指定对方用户ID（卖家联系买家时传入）
     */
    ChatSessionVO startSession(Long userId, Long goodsId, Long otherUserId);

    /**
     * 获取用户的所有聊天会话列表
     */
    List<ChatSessionVO> getMySessions(Long userId);

    /**
     * 获取会话的历史消息(分页)
     */
    List<ChatMessageVO> getMessages(Long sessionId, Long userId, int page, int size);

    /**
     * 发送消息(持久化 + WebSocket 推送)
     */
    ChatMessageVO sendMessage(Long senderId, SendMessageReq req);

    /**
     * 标记消息已读
     */
    void markAsRead(Long sessionId, Long userId);

    /**
     * 撤回消息（2分钟内）
     */
    ChatMessageVO recallMessage(Long userId, Long messageId);
}
