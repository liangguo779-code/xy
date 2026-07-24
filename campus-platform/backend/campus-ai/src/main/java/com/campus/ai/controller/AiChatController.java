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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AiChatController {

    private final AiService aiService;
    private final AiChatHistoryService historyService;
    private final ObjectMapper objectMapper;

    @Value("${ai.service.url:http://localhost:8000}")
    private String aiServiceUrl;

    private final ExecutorService sseExecutor = Executors.newCachedThreadPool();

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
     * 流式问答（SSE 代理到 Python AI 中台，带会话持久化）
     */
    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chatStream(@Valid @RequestBody ChatRequest request) {
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

        // 加载历史消息
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
                }
            } catch (Exception e) {
                log.warn("加载历史消息失败: {}", e.getMessage());
            }
        }

        // 保存用户消息
        historyService.saveMessage(sessionId, "user", request.getQuestion(), null);

        // 创建 SseEmitter（超时 120 秒）
        SseEmitter emitter = new SseEmitter(120000L);
        Long finalSessionId = sessionId;

        sseExecutor.execute(() -> {
            StringBuilder answerBuilder = new StringBuilder();
            String sourcesJson = null;

            try {
                // ✨ 第一时间向前端发送 session 事件，确保前端拿到 sessionId
                emitter.send(SseEmitter.event().data(
                    String.format("{\"type\":\"session\",\"sessionId\":%d}", finalSessionId)
                ));

                // 构建请求体
                String requestBody = objectMapper.writeValueAsString(request);

                // 连接 Python AI 中台
                HttpURLConnection conn = (HttpURLConnection) URI.create(aiServiceUrl + "/chat/stream").toURL().openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(120000);

                try (OutputStream os = conn.getOutputStream()) {
                    os.write(requestBody.getBytes(StandardCharsets.UTF_8));
                }

                // 读取 SSE 流并转发
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (line.startsWith("data: ")) {
                            String data = line.substring(6);

                            // ✨ 用 JSON 解析判断事件类型（不能用字符串 contains，因为 json.dumps 输出 "type": "token" 带空格）
                            try {
                                var node = objectMapper.readTree(data);
                                String type = node.path("type").asText("");
                                if ("token".equals(type)) {
                                    if (node.has("content")) {
                                        answerBuilder.append(node.get("content").asText());
                                    }
                                } else if ("sources".equals(type)) {
                                    if (node.has("sources")) {
                                        sourcesJson = node.get("sources").toString();
                                    }
                                }
                                // stage / done / session 事件：直接透传
                            } catch (Exception ignored) {}

                            // 转发给前端
                            emitter.send(SseEmitter.event().data(data));
                        }
                    }
                }

                // 流结束，保存 AI 回答到数据库
                String answer = answerBuilder.toString();
                if (!answer.isEmpty()) {
                    historyService.saveMessage(finalSessionId, "assistant", answer, sourcesJson);
                    log.info("流式问答完成并保存: sessionId={}, answerLen={}", finalSessionId, answer.length());
                }

                emitter.complete();

            } catch (Exception e) {
                log.error("流式问答失败: sessionId={}, error={}", finalSessionId, e.getMessage());
                try {
                    // 发送错误事件给前端
                    String errorEvent = "{\"type\":\"token\",\"content\":\"抱歉，暂时无法回答您的问题，请稍后重试。\"}";
                    emitter.send(SseEmitter.event().data(errorEvent));
                    emitter.send(SseEmitter.event().data("{\"type\":\"done\"}"));
                    emitter.complete();
                } catch (Exception ignored) {
                    emitter.completeWithError(e);
                }
            }
        });

        emitter.onTimeout(() -> log.warn("SSE 超时: sessionId={}", finalSessionId));
        emitter.onError(e -> log.warn("SSE 错误: sessionId={}, error={}", finalSessionId, e.getMessage()));

        return emitter;
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
