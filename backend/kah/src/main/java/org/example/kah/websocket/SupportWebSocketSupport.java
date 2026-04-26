package org.example.kah.websocket;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.util.Arrays;
import java.util.Map;
import org.example.kah.dto.support.SupportWsCommand;
import org.example.kah.dto.support.SupportWsEnvelope;
import org.example.kah.security.AuthenticatedUser;
import org.example.kah.security.JwtService;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

abstract class SupportWebSocketSupport {

    protected static final String CURRENT_USER_KEY = "currentUser";

    protected final JwtService jwtService;
    protected final ObjectMapper objectMapper;

    protected SupportWebSocketSupport(JwtService jwtService, ObjectMapper objectMapper) {
        this.jwtService = jwtService;
        this.objectMapper = objectMapper;
    }

    protected String extractToken(URI uri) {
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

    protected AuthenticatedUser authenticate(WebSocketSession session) {
        String token = extractToken(session.getUri());
        return jwtService.parseAuthenticatedUser(token);
    }

    protected SupportWsCommand parseCommand(TextMessage message) throws JsonProcessingException {
        return objectMapper.readValue(message.getPayload(), SupportWsCommand.class);
    }

    protected AuthenticatedUser currentUser(WebSocketSession session) {
        return (AuthenticatedUser) session.getAttributes().get(CURRENT_USER_KEY);
    }

    protected void rememberUser(WebSocketSession session, AuthenticatedUser currentUser) {
        session.getAttributes().put(CURRENT_USER_KEY, currentUser);
    }

    protected TextMessage errorMessage(String text) throws JsonProcessingException {
        return new TextMessage(objectMapper.writeValueAsString(new SupportWsEnvelope("ERROR", Map.of("message", text))));
    }
}
