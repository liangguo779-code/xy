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

/** 聊天 WebSocket 处理器：维护在线连接、转发客户端指令（发消息/已读/正在输入），并向在线用户推送消息 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChatWebSocketHandler extends TextWebSocketHandler {

    private final ChatService chatService;
    private final ObjectMapper objectMapper;

    /** userId -> WebSocketSession */
    private static final Map<Long, WebSocketSession> SESSIONS = new ConcurrentHashMap<>();

    /** 连接建立：从握手属性取 userId（拦截器注入），未登录直接拒绝 */
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

    /** 收到文本消息：按 type 分发到发消息/标记已读/正在输入三个处理器，异常统一回传 error 帧 */
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

    /** 连接关闭：从在线表移除该用户的会话 */
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Long userId = (Long) session.getAttributes().get("userId");
        if (userId != null) {
            SESSIONS.remove(userId);
            log.info("WebSocket 连接关闭: userId={}", userId);
        }
    }

    /** 向指定在线用户推送消息（离线则静默丢弃，由业务层落库保证不丢） */
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

    /** 判断用户是否有活跃的 WebSocket 连接 */
    public boolean isOnline(Long userId) {
        WebSocketSession session = SESSIONS.get(userId);
        return session != null && session.isOpen();
    }

    /** 处理 send_msg 指令：组装 SendMessageReq 交给 ChatService 落库并转发 */
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

    /** 处理 read 指令：将会话内消息标记为已读 */
    private void handleMarkRead(Long userId, Map<String, Object> payload) {
        Long sessionId = Long.valueOf(payload.get("sessionId").toString());
        chatService.markAsRead(sessionId, userId);
    }

    /** 处理 typing 指令：把"正在输入"状态转发给目标用户 */
    private void handleTyping(Long userId, Map<String, Object> payload) {
        Long targetUserId = Long.valueOf(payload.get("targetUserId").toString());
        pushToUser(targetUserId, Map.of("type", "typing", "userId", userId));
    }

    private void sendMessage(WebSocketSession session, Object message) throws IOException {
        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(message)));
    }
}
