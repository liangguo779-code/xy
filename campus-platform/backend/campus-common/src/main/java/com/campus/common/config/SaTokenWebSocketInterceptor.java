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
 * WebSocket 握手拦截器：从 URL 参数中取 token 进行鉴权。
 * 浏览器 WebSocket 无法自定义请求头，故登录态通过 ws 地址的 token 查询参数携带。
 */
@Component
public class SaTokenWebSocketInterceptor implements HandshakeInterceptor {

    /**
     * 握手前校验：解析 token 得到登录用户并写入 attributes 供后续 handler 使用。
     *
     * @return token 有效返回 true 允许建立连接；无效或缺失返回 false 拒绝握手
     */
    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        if (request instanceof ServletServerHttpRequest servletReq) {
            String token = servletReq.getServletRequest().getParameter("token");
            if (token != null) {
                try {
                    Object loginId = StpUtil.getLoginIdByToken(token);
                    if (loginId != null) {
                        // userId 供 WebSocket 消息处理器区分连接归属
                        attributes.put("userId", Long.parseLong(loginId.toString()));
                        return true;
                    }
                } catch (Exception ignored) {
                    // token 非法或已过期：走下方 return false 拒绝连接，无需额外处理
                }
            }
        }
        return false;
    }

    /** 握手后回调：本项目无清理动作，留空实现 */
    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
    }
}
