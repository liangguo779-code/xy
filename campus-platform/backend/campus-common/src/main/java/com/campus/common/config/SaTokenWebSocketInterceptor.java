package com.campus.common.config;

import cn.dev33.satoken.stp.StpUtil;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

/**
 * WebSocket 握手拦截器：从 URL 参数中取 token 进行鉴权
 */
@Component
public class SaTokenWebSocketInterceptor implements HandshakeInterceptor {

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        if (request instanceof ServletServerHttpRequest servletReq) {
            String token = servletReq.getServletRequest().getParameter("token");
            if (token != null) {
                try {
                    Object loginId = StpUtil.getLoginIdByToken(token);
                    if (loginId != null) {
                        attributes.put("userId", Long.parseLong(loginId.toString()));
                        return true;
                    }
                } catch (Exception ignored) {
                }
            }
        }
        return false;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
    }
}
