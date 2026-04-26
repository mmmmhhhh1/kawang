package org.example.kah.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import org.example.kah.dto.admin.AdminNotificationEvent;
import org.example.kah.dto.support.SupportDispatchPayload;
import org.example.kah.dto.support.SupportUnreadView;
import org.example.kah.dto.support.SupportWsEnvelope;
import org.example.kah.security.AuthenticatedUser;
import org.example.kah.security.UserScope;
import org.example.kah.service.AdminMobilePushService;
import org.example.kah.service.SupportRealtimeService;
import org.example.kah.support.AsyncTaskSupport;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;

@Service
@RequiredArgsConstructor
public class SupportRealtimeServiceImpl implements SupportRealtimeService {

    private static final int SEND_TIME_LIMIT_MILLIS = 10_000;
    private static final int BUFFER_SIZE_LIMIT_BYTES = 256 * 1024;

    private final ObjectMapper objectMapper;
    private final AdminMobilePushService adminMobilePushService;
    private final AsyncTaskSupport asyncTaskSupport;

    private final Map<Long, Map<String, WebSocketSession>> memberSessions = new ConcurrentHashMap<>();
    private final Map<String, WebSocketSession> adminSessions = new ConcurrentHashMap<>();
    private final Map<String, AuthenticatedUser> sessionUsers = new ConcurrentHashMap<>();

    @Override
    public boolean registerMember(WebSocketSession session, AuthenticatedUser currentUser) {
        WebSocketSession concurrentSession = decorate(session);
        sessionUsers.put(session.getId(), currentUser);
        memberSessions.computeIfAbsent(currentUser.userId(), ignored -> new ConcurrentHashMap<>())
                .put(session.getId(), concurrentSession);
        return true;
    }

    @Override
    public boolean registerAdmin(WebSocketSession session, AuthenticatedUser currentUser) {
        WebSocketSession concurrentSession = decorate(session);
        sessionUsers.put(session.getId(), currentUser);
        adminSessions.put(session.getId(), concurrentSession);
        return true;
    }

    @Override
    public void unregister(WebSocketSession session) {
        if (session == null) {
            return;
        }
        AuthenticatedUser currentUser = sessionUsers.remove(session.getId());
        if (currentUser != null && UserScope.MEMBER.equals(currentUser.scope())) {
            Map<String, WebSocketSession> sessions = memberSessions.get(currentUser.userId());
            if (sessions != null) {
                sessions.remove(session.getId());
                if (sessions.isEmpty()) {
                    memberSessions.remove(currentUser.userId());
                }
            }
        }
        adminSessions.remove(session.getId());
    }

    @Override
    public void dispatchSupportMessage(SupportDispatchPayload payload) {
        asyncTaskSupport.runRealtimeAsync(() -> {
            SupportWsEnvelope envelope = new SupportWsEnvelope("SUPPORT_DISPATCH", payload);
            sendToMember(payload.adminSession().memberId(), envelope);
            sendToAdmins(envelope);
        });

        if (UserScope.MEMBER.equals(payload.message().senderScope())) {
            adminMobilePushService.sendSupportMessage(payload.adminSession(), payload.message());
        }
    }

    @Override
    public void dispatchUnreadUpdated(Long memberId, SupportUnreadView unread) {
        asyncTaskSupport.runRealtimeAsync(() -> {
            sendToMember(memberId, new SupportWsEnvelope("UNREAD_UPDATED", unread));
            sendToAdmins(new SupportWsEnvelope("UNREAD_UPDATED", unread));
        });
    }

    @Override
    public void dispatchRechargeCreated(AdminNotificationEvent event) {
        asyncTaskSupport.runRealtimeAsync(() -> sendToAdmins(new SupportWsEnvelope("RECHARGE_CREATED", event)));
        adminMobilePushService.sendRechargeCreated(event);
    }

    private void sendToMember(Long memberId, Object payload) {
        Map<String, WebSocketSession> sessions = memberSessions.get(memberId);
        if (sessions == null || sessions.isEmpty()) {
            return;
        }
        String json = toJson(payload);
        for (WebSocketSession session : sessions.values()) {
            send(session, json);
        }
    }

    private void sendToAdmins(Object payload) {
        if (adminSessions.isEmpty()) {
            return;
        }
        String json = toJson(payload);
        for (WebSocketSession session : adminSessions.values()) {
            send(session, json);
        }
    }

    private void send(WebSocketSession session, String json) {
        if (session == null || !session.isOpen()) {
            unregister(session);
            return;
        }
        try {
            session.sendMessage(new TextMessage(json));
        } catch (IOException exception) {
            unregister(session);
            try {
                session.close();
            } catch (IOException ignored) {
            }
        }
    }

    private WebSocketSession decorate(WebSocketSession session) {
        return new ConcurrentWebSocketSessionDecorator(
                session,
                SEND_TIME_LIMIT_MILLIS,
                BUFFER_SIZE_LIMIT_BYTES);
    }

    private String toJson(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize support websocket payload", exception);
        }
    }
}
