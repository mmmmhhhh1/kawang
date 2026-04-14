package org.example.kah.config;

import lombok.RequiredArgsConstructor;
import org.example.kah.websocket.AdminNotificationWebSocketHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * 管理端通知 WebSocket 配置。
 */
@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class AdminNotificationWebSocketConfig implements WebSocketConfigurer {

    private final AdminNotificationWebSocketHandler adminNotificationWebSocketHandler;
    private final ShopCorsProperties shopCorsProperties;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(adminNotificationWebSocketHandler, "/ws/admin/notifications")
                .setAllowedOrigins(shopCorsProperties.allowedOrigins().toArray(String[]::new));
    }
}