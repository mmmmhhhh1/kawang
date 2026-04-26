package org.example.kah.config;

import lombok.RequiredArgsConstructor;
import org.example.kah.websocket.AdminSupportWebSocketHandler;
import org.example.kah.websocket.MemberSupportWebSocketHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class SupportWebSocketConfig implements WebSocketConfigurer {

    private final MemberSupportWebSocketHandler memberSupportWebSocketHandler;
    private final AdminSupportWebSocketHandler adminSupportWebSocketHandler;
    private final ShopCorsProperties shopCorsProperties;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        String[] allowedOrigins = shopCorsProperties.allowedOrigins().toArray(String[]::new);
        registry.addHandler(memberSupportWebSocketHandler, "/ws/member/support")
                .setAllowedOrigins(allowedOrigins);
        registry.addHandler(adminSupportWebSocketHandler, "/ws/admin/support")
                .setAllowedOrigins(allowedOrigins);
    }
}
