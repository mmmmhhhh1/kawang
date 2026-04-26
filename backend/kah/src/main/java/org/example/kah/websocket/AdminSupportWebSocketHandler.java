package org.example.kah.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.kah.dto.support.SupportDispatchPayload;
import org.example.kah.dto.support.SupportWsCommand;
import org.example.kah.security.AuthenticatedUser;
import org.example.kah.security.JwtService;
import org.example.kah.security.UserScope;
import org.example.kah.service.SupportChatService;
import org.example.kah.service.SupportRealtimeService;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@Component
public class AdminSupportWebSocketHandler extends TextWebSocketHandler {

    private final SupportWebSocketSupport support;
    private final SupportRealtimeService supportRealtimeService;
    private final SupportChatService supportChatService;

    public AdminSupportWebSocketHandler(
            JwtService jwtService,
            ObjectMapper objectMapper,
            SupportRealtimeService supportRealtimeService,
            SupportChatService supportChatService) {
        this.support = new SupportWebSocketSupport(jwtService, objectMapper) {
        };
        this.supportRealtimeService = supportRealtimeService;
        this.supportChatService = supportChatService;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        AuthenticatedUser currentUser = support.authenticate(session);
        if (currentUser == null || !UserScope.ADMIN.equals(currentUser.scope()) || !supportRealtimeService.registerAdmin(session, currentUser)) {
            session.close(CloseStatus.NOT_ACCEPTABLE.withReason("admin token required"));
            return;
        }
        support.rememberUser(session, currentUser);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        try {
            SupportWsCommand command = support.parseCommand(message);
            AuthenticatedUser currentUser = support.currentUser(session);
            if (currentUser == null) {
                session.close(CloseStatus.NOT_ACCEPTABLE.withReason("admin token required"));
                return;
            }
            if (!"SEND_MESSAGE".equals(command.type())) {
                synchronized (session) {
                    session.sendMessage(support.errorMessage("unsupported command"));
                }
                return;
            }
            SupportDispatchPayload payload = supportChatService.sendAdminMessage(currentUser, command.sessionId(), command.content());
            supportRealtimeService.dispatchSupportMessage(payload);
        } catch (Exception exception) {
            if (session.isOpen()) {
                synchronized (session) {
                    session.sendMessage(support.errorMessage(exception.getMessage() == null ? "send failed" : exception.getMessage()));
                }
            }
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        supportRealtimeService.unregister(session);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        supportRealtimeService.unregister(session);
        if (session.isOpen()) {
            session.close(CloseStatus.SERVER_ERROR);
        }
    }
}
