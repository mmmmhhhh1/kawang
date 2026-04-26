package org.example.kah.service;

import org.example.kah.dto.admin.AdminNotificationEvent;
import org.example.kah.dto.support.SupportDispatchPayload;
import org.example.kah.dto.support.SupportUnreadView;
import org.example.kah.security.AuthenticatedUser;
import org.springframework.web.socket.WebSocketSession;

public interface SupportRealtimeService {

    boolean registerMember(WebSocketSession session, AuthenticatedUser currentUser);

    boolean registerAdmin(WebSocketSession session, AuthenticatedUser currentUser);

    void unregister(WebSocketSession session);

    void dispatchSupportMessage(SupportDispatchPayload payload);

    void dispatchUnreadUpdated(Long memberId, SupportUnreadView unread);

    void dispatchRechargeCreated(AdminNotificationEvent event);
}
