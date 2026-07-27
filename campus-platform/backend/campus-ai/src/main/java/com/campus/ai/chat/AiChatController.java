package com.campus.ai.chat;

import cn.dev33.satoken.stp.StpUtil;
import com.campus.ai.chat.dto.*;
import com.campus.ai.chat.AiChatHistoryService;
import com.campus.ai.rag.RagOrchestrator;
import com.campus.common.result.R;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AiChatController {

    private final RagOrchestrator ragOrchestrator;
    private final AiChatHistoryService historyService;
    private final ObjectMapper objectMapper;

    private final ExecutorService sseExecutor = Executors.newCachedThreadPool();

    @PostMapping("/chat")
    public R<ChatResponse> chat(@Valid @RequestBody ChatRequest request) {
        Long userId = safeUserId();
        Long sessionId = request.getSessionId();
        if (sessionId == null) {
            AiChatSessionVO session = historyService.createSession(userId,
                    request.getQuestion().length() > 30
                            ? request.getQuestion().substring(0, 30) + "..."
                            : request.getQuestion());
            sessionId = session.getId();
        }
        if (request.getHistory() == null || request.getHistory().isEmpty()) {
            request.setHistory(loadHistory(sessionId, userId));
        }
        historyService.saveMessage(sessionId, "user", request.getQuestion(), null);
        ChatResponse response = ragOrchestrator.run(request);
        response.setSessionId(sessionId);
        try {
            String sourcesJson = response.getSources() != null
                    ? objectMapper.writeValueAsString(response.getSources()) : null;
            historyService.saveMessage(sessionId, "assistant", response.getAnswer(), sourcesJson);
        } catch (Exception e) {
            historyService.saveMessage(sessionId, "assistant", response.getAnswer(), null);
        }
        return R.ok(response);
    }

    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chatStream(@Valid @RequestBody ChatRequest request) {
        Long userId = safeUserId();
        Long sessionId = request.getSessionId();
        if (sessionId == null) {
            AiChatSessionVO session = historyService.createSession(userId,
                    request.getQuestion().length() > 30
                            ? request.getQuestion().substring(0, 30) + "..."
                            : request.getQuestion());
            sessionId = session.getId();
        }
        if (request.getHistory() == null || request.getHistory().isEmpty()) {
            request.setHistory(loadHistory(sessionId, userId));
        }
        historyService.saveMessage(sessionId, "user", request.getQuestion(), null);

        SseEmitter emitter = new SseEmitter(120_000L);
        Long finalSessionId = sessionId;
        sseExecutor.execute(() -> {
            StringBuilder answerBuilder = new StringBuilder();
            java.util.concurrent.atomic.AtomicReference<String> sourcesJsonRef = new java.util.concurrent.atomic.AtomicReference<>();
            try {
                emitter.send(SseEmitter.event().data(
                        String.format("{\"type\":\"session\",\"sessionId\":%d}", finalSessionId)));

                ragOrchestrator.run(request, event -> {
                    try {
                        switch (event.type()) {
                            case "stage" -> emitter.send(SseEmitter.event().data(objectMapper.writeValueAsString(
                                    Map.of("type", "stage", "stage", event.stage(), "message", event.payload()))));
                            case "sources" -> {
                                String s = objectMapper.writeValueAsString(event.payload());
                                sourcesJsonRef.set(s);
                                emitter.send(SseEmitter.event().data(
                                        String.format("{\"type\":\"sources\",\"sources\":%s}", s)));
                            }
                            case "done" -> emitter.send(SseEmitter.event().data("{\"type\":\"done\"}"));
                        }
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                });

                // Run the orchestrator once more synchronously to capture the final answer
                // text for persistence. The streaming run above already pushed the events
                // to the client; this one is for the DB row only.
                ChatResponse finalResp = ragOrchestrator.run(request);
                answerBuilder.append(finalResp.getAnswer() == null ? "" : finalResp.getAnswer());
                if (finalResp.getSources() != null && sourcesJsonRef.get() == null) {
                    sourcesJsonRef.set(objectMapper.writeValueAsString(finalResp.getSources()));
                }
                if (answerBuilder.length() > 0) {
                    historyService.saveMessage(finalSessionId, "assistant",
                            answerBuilder.toString(), sourcesJsonRef.get());
                }
                emitter.complete();
            } catch (Exception e) {
                log.error("SSE chat failed: sessionId={}, error={}", finalSessionId, e.getMessage());
                try {
                    emitter.send(SseEmitter.event().data(
                            "{\"type\":\"token\",\"content\":\"抱歉，暂时无法回答您的问题，请稍后重试。\"}"));
                    emitter.send(SseEmitter.event().data("{\"type\":\"done\"}"));
                    emitter.complete();
                } catch (Exception ignored) {
                    emitter.completeWithError(e);
                }
            }
        });

        emitter.onTimeout(() -> log.warn("SSE timeout: sessionId={}", finalSessionId));
        emitter.onError(e -> log.warn("SSE error: sessionId={}, error={}", finalSessionId, e.getMessage()));
        return emitter;
    }

    @GetMapping("/sessions")
    public R<List<AiChatSessionVO>> sessions() {
        Long userId = safeUserId();
        return R.ok(historyService.getMySessions(userId));
    }

    @GetMapping("/sessions/{sessionId}/messages")
    public R<List<AiChatMessageVO>> messages(@PathVariable Long sessionId) {
        Long userId = safeUserId();
        return R.ok(historyService.getMessages(sessionId, userId));
    }

    @PostMapping("/sessions")
    public R<AiChatSessionVO> createSession(@RequestBody(required = false) CreateSessionReq req) {
        Long userId = safeUserId();
        return R.ok(historyService.createSession(userId, req != null ? req.getTitle() : null));
    }

    @DeleteMapping("/sessions/{sessionId}")
    public R<Void> deleteSession(@PathVariable Long sessionId) {
        Long userId = safeUserId();
        historyService.deleteSession(userId, sessionId);
        return R.ok();
    }

    @PutMapping("/sessions/{sessionId}/title")
    public R<Void> updateTitle(@PathVariable Long sessionId, @RequestBody UpdateTitleReq req) {
        Long userId = safeUserId();
        historyService.updateTitle(userId, sessionId, req.getTitle());
        return R.ok();
    }

    private List<ChatRequest.HistoryItem> loadHistory(Long sessionId, Long userId) {
        try {
            List<AiChatMessageVO> msgs = historyService.getMessages(sessionId, userId);
            if (msgs == null || msgs.isEmpty()) return List.of();
            return msgs.stream().map(m -> {
                ChatRequest.HistoryItem item = new ChatRequest.HistoryItem();
                item.setRole(m.getRole());
                item.setContent(m.getContent());
                return item;
            }).toList();
        } catch (Exception e) {
            log.warn("Load history failed: {}", e.getMessage());
            return List.of();
        }
    }

    private Long safeUserId() {
        try {
            return StpUtil.getLoginIdAsLong();
        } catch (Exception e) {
            return 0L;
        }
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
