package com.campus.ai.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.campus.ai.dto.*;
import com.campus.ai.service.AiChatHistoryService;
import com.campus.ai.service.AiService;
import com.campus.common.result.R;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AiChatController {

    private final AiService aiService;
    private final AiChatHistoryService historyService;
    private final ObjectMapper objectMapper;

    /**
     * 发送消息（带会话持久化）
     */
    @PostMapping("/chat")
    public R<ChatResponse> chat(@Valid @RequestBody ChatRequest request) {
        Long userId = StpUtil.getLoginIdAsLong();

        // 自动创建会话
        Long sessionId = request.getSessionId();
        if (sessionId == null) {
            AiChatSessionVO session = historyService.createSession(userId,
                    request.getQuestion().length() > 30
                            ? request.getQuestion().substring(0, 30) + "..."
                            : request.getQuestion());
            sessionId = session.getId();
        }

        // 从数据库加载历史消息作为上下文
        if (request.getHistory() == null || request.getHistory().isEmpty()) {
            try {
                List<AiChatMessageVO> historyMsgs = historyService.getMessages(sessionId, userId);
                if (historyMsgs != null && !historyMsgs.isEmpty()) {
                    List<ChatRequest.HistoryItem> history = historyMsgs.stream()
                            .map(m -> {
                                ChatRequest.HistoryItem item = new ChatRequest.HistoryItem();
                                item.setRole(m.getRole());
                                item.setContent(m.getContent());
                                return item;
                            })
                            .toList();
                    request.setHistory(history);
                    log.info("加载历史消息: sessionId={}, count={}", sessionId, history.size());
                }
            } catch (Exception e) {
                log.warn("加载历史消息失败: sessionId={}, error={}", sessionId, e.getMessage());
            }
        }

        // 保存用户消息
        historyService.saveMessage(sessionId, "user", request.getQuestion(), null);

        // 调用 AI（带历史上下文）
        ChatResponse response = aiService.chat(request);
        response.setSessionId(sessionId);

        // 保存 AI 回答
        try {
            String sourcesJson = response.getSources() != null
                    ? objectMapper.writeValueAsString(response.getSources()) : null;
            historyService.saveMessage(sessionId, "assistant", response.getAnswer(), sourcesJson);
        } catch (Exception e) {
            historyService.saveMessage(sessionId, "assistant", response.getAnswer(), null);
        }

        return R.ok(response);
    }

    /**
     * 获取历史会话列表
     */
    @GetMapping("/sessions")
    public R<List<AiChatSessionVO>> sessions() {
        Long userId = StpUtil.getLoginIdAsLong();
        return R.ok(historyService.getMySessions(userId));
    }

    /**
     * 获取会话消息
     */
    @GetMapping("/sessions/{sessionId}/messages")
    public R<List<AiChatMessageVO>> messages(@PathVariable Long sessionId) {
        Long userId = StpUtil.getLoginIdAsLong();
        return R.ok(historyService.getMessages(sessionId, userId));
    }

    /**
     * 创建新会话
     */
    @PostMapping("/sessions")
    public R<AiChatSessionVO> createSession(@RequestBody(required = false) CreateSessionReq req) {
        Long userId = StpUtil.getLoginIdAsLong();
        String title = req != null ? req.getTitle() : null;
        return R.ok(historyService.createSession(userId, title));
    }

    /**
     * 删除会话
     */
    @DeleteMapping("/sessions/{sessionId}")
    public R<Void> deleteSession(@PathVariable Long sessionId) {
        Long userId = StpUtil.getLoginIdAsLong();
        historyService.deleteSession(userId, sessionId);
        return R.ok();
    }

    /**
     * 更新会话标题
     */
    @PutMapping("/sessions/{sessionId}/title")
    public R<Void> updateTitle(@PathVariable Long sessionId, @RequestBody UpdateTitleReq req) {
        Long userId = StpUtil.getLoginIdAsLong();
        historyService.updateTitle(userId, sessionId, req.getTitle());
        return R.ok();
    }

    @lombok.Data
    public static class CreateSessionReq {
        private String title;
    }

    @lombok.Data
    public static class UpdateTitleReq {
        private String title;
    }
}
