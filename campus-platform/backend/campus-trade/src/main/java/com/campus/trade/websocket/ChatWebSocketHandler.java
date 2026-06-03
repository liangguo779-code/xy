package com.campus.trade.websocket;

import com.campus.trade.dto.SendMessageReq;
import com.campus.trade.service.ChatService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class ChatWebSocketHandler extends TextWebSocketHandler {

    private final ChatService chatService;
    private final ObjectMapper objectMapper;

    /** userId -> WebSocketSession */
    private static final Map<Long, WebSocketSession> SESSIONS = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        Long userId = (Long) session.getAttributes().get("userId");
        if (userId == null) {
            session.close(CloseStatus.NOT_ACCEPTABLE);
            return;
        }
        SESSIONS.put(userId, session);
        log.info("WebSocket 连接建立: userId={}", userId);
        sendMessage(session, Map.of("type", "connected", "userId", userId));
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        Long userId = (Long) session.getAttributes().get("userId");
        if (userId == null) return;

        try {
            Map<String, Object> payload = objectMapper.readValue(message.getPayload(), Map.class);
            String type = (String) payload.get("type");

            if ("send_msg".equals(type)) {
                handleSendMessage(userId, payload);
            } else if ("read".equals(type)) {
                handleMarkRead(userId, payload);
            } else if ("typing".equals(type)) {
                handleTyping(userId, payload);
            }
        } catch (Exception e) {
            log.error("处理 WebSocket 消息失败", e);
            sendMessage(session, Map.of("type", "error", "msg", e.getMessage()));
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Long userId = (Long) session.getAttributes().get("userId");
        if (userId != null) {
            SESSIONS.remove(userId);
            log.info("WebSocket 连接关闭: userId={}", userId);
        }
    }

    public void pushToUser(Long userId, Object message) {
        WebSocketSession session = SESSIONS.get(userId);
        if (session != null && session.isOpen()) {
            try {
                sendMessage(session, message);
            } catch (Exception e) {
                log.error("推送消息失败: userId={}", userId, e);
            }
        }
    }

    public boolean isOnline(Long userId) {
        WebSocketSession session = SESSIONS.get(userId);
        return session != null && session.isOpen();
    }

    private void handleSendMessage(Long senderId, Map<String, Object> payload) {
        Long sessionId = Long.valueOf(payload.get("sessionId").toString());
        String content = (String) payload.get("content");
        Integer msgType = payload.get("msgType") != null
                ? Integer.valueOf(payload.get("msgType").toString()) : 0;

        // 处理 extra 字段：字符串直接用，对象转JSON
        String extra = null;
        if (payload.get("extra") != null) {
            Object extraVal = payload.get("extra");
            if (extraVal instanceof String) {
                extra = (String) extraVal;
            } else {
                try {
                    extra = objectMapper.writeValueAsString(extraVal);
                } catch (Exception ignored) {
                    extra = extraVal.toString();
                }
            }
        }

        SendMessageReq req = new SendMessageReq();
        req.setSessionId(sessionId);
        req.setContent(content);
        req.setMsgType(msgType);
        req.setExtra(extra);

        chatService.sendMessage(senderId, req);
    }

    private void handleMarkRead(Long userId, Map<String, Object> payload) {
        Long sessionId = Long.valueOf(payload.get("sessionId").toString());
        chatService.markAsRead(sessionId, userId);
    }

    private void handleTyping(Long userId, Map<String, Object> payload) {
        Long targetUserId = Long.valueOf(payload.get("targetUserId").toString());
        pushToUser(targetUserId, Map.of("type", "typing", "userId", userId));
    }

    private void sendMessage(WebSocketSession session, Object message) throws IOException {
        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(message)));
    }
}
