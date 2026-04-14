package org.example.kah.websocket;

import java.net.URI;
import java.util.Arrays;
import lombok.RequiredArgsConstructor;
import org.example.kah.service.AdminNotificationService;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

/**
 * 管理端通知 WebSocket 处理器。
 */
@Component
@RequiredArgsConstructor
public class AdminNotificationWebSocketHandler extends TextWebSocketHandler {

    private final AdminNotificationService adminNotificationService;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String token = extractToken(session.getUri());
        if (!adminNotificationService.register(session, token)) {
            session.close(CloseStatus.NOT_ACCEPTABLE.withReason("admin token required"));
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        // 管理端通知通道是单向推送，忽略客户端消息。
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        adminNotificationService.unregister(session);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        adminNotificationService.unregister(session);
        if (session.isOpen()) {
            session.close(CloseStatus.SERVER_ERROR);
        }
    }

    private String extractToken(URI uri) {
        if (uri == null || uri.getQuery() == null || uri.getQuery().isBlank()) {
            return null;
        }
        return Arrays.stream(uri.getQuery().split("&"))
                .map(item -> item.split("=", 2))
                .filter(item -> item.length == 2 && "token".equals(item[0]))
                .map(item -> item[1])
                .findFirst()
                .orElse(null);
    }
}