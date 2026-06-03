package com.campus.trade.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.campus.common.result.R;
import com.campus.trade.dto.*;
import com.campus.trade.service.ChatService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    @PostMapping("/session")
    public R<ChatSessionVO> startSession(@RequestParam Long goodsId) {
        Long userId = StpUtil.getLoginIdAsLong();
        return R.ok(chatService.startSession(userId, goodsId));
    }

    @GetMapping("/sessions")
    public R<List<ChatSessionVO>> mySessions() {
        Long userId = StpUtil.getLoginIdAsLong();
        return R.ok(chatService.getMySessions(userId));
    }

    @GetMapping("/messages/{sessionId}")
    public R<List<ChatMessageVO>> messages(
            @PathVariable Long sessionId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "50") int size) {
        Long userId = StpUtil.getLoginIdAsLong();
        return R.ok(chatService.getMessages(sessionId, userId, page, size));
    }

    @PostMapping("/messages")
    public R<ChatMessageVO> sendMessage(@Valid @RequestBody SendMessageReq req) {
        Long userId = StpUtil.getLoginIdAsLong();
        return R.ok(chatService.sendMessage(userId, req));
    }
}
