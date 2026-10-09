package com.campus.trade.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.campus.common.result.R;
import com.campus.trade.dto.*;
import com.campus.trade.service.ChatService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** 聊天 REST 接口：建会话、拉消息、发消息、撤回（实时推送走 WebSocket） */
@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    /** 发起（或复用）与某商品卖家/指定用户的会话 */
    @PostMapping("/session")
    public R<ChatSessionVO> startSession(
            @RequestParam Long goodsId,
            @RequestParam(required = false) Long otherUserId) {
        Long userId = StpUtil.getLoginIdAsLong();
        return R.ok(chatService.startSession(userId, goodsId, otherUserId));
    }

    /** 获取我的会话列表（按最后消息时间排序） */
    @GetMapping("/sessions")
    public R<List<ChatSessionVO>> mySessions() {
        Long userId = StpUtil.getLoginIdAsLong();
        return R.ok(chatService.getMySessions(userId));
    }

    /** 分页拉取会话内消息 */
    @GetMapping("/messages/{sessionId}")
    public R<List<ChatMessageVO>> messages(
            @PathVariable Long sessionId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "50") int size) {
        Long userId = StpUtil.getLoginIdAsLong();
        return R.ok(chatService.getMessages(sessionId, userId, page, size));
    }

    /** 发送消息（REST 通道，等价于 WebSocket 的 send_msg） */
    @PostMapping("/messages")
    public R<ChatMessageVO> sendMessage(@Valid @RequestBody SendMessageReq req) {
        Long userId = StpUtil.getLoginIdAsLong();
        return R.ok(chatService.sendMessage(userId, req));
    }

    /** 撤回自己发出的消息 */
    @PutMapping("/messages/{id}/recall")
    public R<ChatMessageVO> recallMessage(@PathVariable Long id) {
        Long userId = StpUtil.getLoginIdAsLong();
        return R.ok(chatService.recallMessage(userId, id));
    }
}
