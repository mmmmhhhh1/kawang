package org.example.kah.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import org.example.kah.dto.admin.AdminNotificationEvent;
import org.example.kah.security.AuthenticatedUser;
import org.example.kah.security.JwtService;
import org.example.kah.security.UserScope;
import org.example.kah.service.AdminNotificationService;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

/**
 * 管理端通知服务默认实现。
 */
@Service
@RequiredArgsConstructor
public class AdminNotificationServiceImpl implements AdminNotificationService {

    private final JwtService jwtService;
    private final ObjectMapper objectMapper;
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

    @Override
    public boolean register(WebSocketSession session, String token) {
        AuthenticatedUser currentUser = jwtService.parseAuthenticatedUser(token);
        if (currentUser == null || !UserScope.ADMIN.equals(currentUser.scope())) {
            return false;
        }
        sessions.put(session.getId(), session);
        return true;
    }

    @Override
    public void unregister(WebSocketSession session) {
        if (session != null) {
            sessions.remove(session.getId());
        }
    }

    @Override
    public void broadcast(AdminNotificationEvent event) {
        String payload = toJson(event);
        for (WebSocketSession session : sessions.values()) {
            if (!session.isOpen()) {
                sessions.remove(session.getId());
                continue;
            }
            try {
                synchronized (session) {
                    session.sendMessage(new TextMessage(payload));
                }
            } catch (IOException exception) {
                sessions.remove(session.getId());
                try {
                    session.close();
                } catch (IOException ignored) {
                }
            }
        }
    }

    private String toJson(AdminNotificationEvent event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize admin notification event", exception);
        }
    }
}