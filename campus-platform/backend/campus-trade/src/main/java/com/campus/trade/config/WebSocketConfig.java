package com.campus.trade.config;

import com.campus.common.config.SaTokenWebSocketInterceptor;
import com.campus.trade.websocket.ChatWebSocketHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/** WebSocket 配置，注册聊天端点并挂接 Sa-Token 登录校验拦截器 */
@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer {

    private final ChatWebSocketHandler chatWebSocketHandler;
    private final SaTokenWebSocketInterceptor saTokenWebSocketInterceptor;

    /** 注册 /ws/chat 端点：握手前经 Sa-Token 鉴权，允许跨域来源 */
    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(chatWebSocketHandler, "/ws/chat")
                .addInterceptors(saTokenWebSocketInterceptor)
                .setAllowedOrigins("*");
    }
}
