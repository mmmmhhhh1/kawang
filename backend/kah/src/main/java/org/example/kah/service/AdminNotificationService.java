package org.example.kah.service;

import org.example.kah.dto.admin.AdminNotificationEvent;
import org.springframework.web.socket.WebSocketSession;

public interface AdminNotificationService {

    boolean register(WebSocketSession session, String token);

    void unregister(WebSocketSession session);

    void broadcast(AdminNotificationEvent event);
}