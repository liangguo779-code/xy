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
    public SseEmitter chatStream(@Valid @RequestBody ChatRequest request,
                                  jakarta.servlet.http.HttpServletResponse response) {
        // Force UTF-8 encoding for SSE response. Spring's SseEmitter ignores the
        // charset parameter on the produces attribute and defaults to ISO-8859-1,
        // which corrupts multi-byte UTF-8 characters.
        response.setCharacterEncoding("UTF-8");
        response.setContentType("text/event-stream;charset=UTF-8");
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
            java.util.concurrent.atomic.AtomicReference<String> answerRef = new java.util.concurrent.atomic.AtomicReference<>("");
            java.util.concurrent.atomic.AtomicReference<String> sourcesJsonRef = new java.util.concurrent.atomic.AtomicReference<>();
            try {
                sendJson(emitter, Map.of("type", "session", "sessionId", finalSessionId));

                ChatResponse resp = ragOrchestrator.run(request, event -> {
                    try {
                        switch (event.type()) {
                            case "stage" -> sendJson(emitter, Map.of("type", "stage", "stage", event.stage(), "message", event.payload()));
                            case "sources" -> {
                                sourcesJsonRef.set(objectMapper.writeValueAsString(event.payload()));
                                sendJson(emitter, Map.of("type", "sources", "sources", event.payload()));
                            }
                            case "token" -> sendJson(emitter, Map.of("type", "token", "content", event.payload()));
                            case "done" -> sendJson(emitter, Map.of("type", "done"));
                        }
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                });

                // The orchestrator's return value already contains the fully streamed answer
                // (streamGenerate now blocks until completion), so no need for a second run.
                answerRef.set(resp.getAnswer() == null ? "" : resp.getAnswer());
                if (resp.getSources() != null && sourcesJsonRef.get() == null) {
                    sourcesJsonRef.set(objectMapper.writeValueAsString(resp.getSources()));
                }
                if (!answerRef.get().isEmpty()) {
                    historyService.saveMessage(finalSessionId, "assistant",
                            answerRef.get(), sourcesJsonRef.get());
                }
                emitter.complete();
            } catch (Exception e) {
                log.error("SSE chat failed: sessionId={}, error={}", finalSessionId, e.getMessage());
                try {
                    sendJson(emitter, Map.of("type", "token", "content", "抱歉，暂时无法回答您的问题，请稍后重试。"));
                    sendJson(emitter, Map.of("type", "done"));
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

    /**
     * Send an SSE event with UTF-8 encoded JSON payload.
     * The response is forced to UTF-8 in the controller method, so SseEmitter
     * will write the String using UTF-8 encoding.
     */
    private void sendJson(SseEmitter emitter, Object payload) throws Exception {
        emitter.send(SseEmitter.event().data(objectMapper.writeValueAsString(payload)));
    }
}
