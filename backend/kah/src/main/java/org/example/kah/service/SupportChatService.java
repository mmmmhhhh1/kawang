package org.example.kah.service;

import org.example.kah.common.CursorPageResponse;
import org.example.kah.dto.admin.AdminSupportSessionItemView;
import org.example.kah.dto.publicapi.MemberSupportSessionView;
import org.example.kah.dto.support.SupportDispatchPayload;
import org.example.kah.dto.support.SupportMessageView;
import org.example.kah.dto.support.SupportUnreadView;
import org.example.kah.security.AuthenticatedUser;
import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;
import java.time.LocalDateTime;

public interface SupportChatService {

    MemberSupportSessionView getOrCreateMemberSession(AuthenticatedUser currentUser);

    CursorPageResponse<SupportMessageView> listMemberMessages(AuthenticatedUser currentUser, int size, String cursor, String after);

    SupportUnreadView markMemberRead(AuthenticatedUser currentUser);

    CursorPageResponse<AdminSupportSessionItemView> listAdminSessions(
            AuthenticatedUser currentUser,
            int size,
            String cursor,
            String status,
            String keyword);

    CursorPageResponse<AdminSupportSessionItemView> listAdminSessionsUpdatedAfter(
            AuthenticatedUser currentUser,
            int size,
            LocalDateTime updatedAfter,
            String status,
            String keyword);

    CursorPageResponse<SupportMessageView> listAdminMessages(
            AuthenticatedUser currentUser,
            Long sessionId,
            int size,
            String cursor,
            String after);

    SupportUnreadView markAdminRead(AuthenticatedUser currentUser, Long sessionId);

    SupportDispatchPayload sendMemberMessage(AuthenticatedUser currentUser, Long sessionId, String content);

    SupportDispatchPayload sendAdminMessage(AuthenticatedUser currentUser, Long sessionId, String content);

    SupportDispatchPayload sendMemberAttachment(AuthenticatedUser currentUser, Long sessionId, String content, MultipartFile file);

    SupportDispatchPayload sendAdminAttachment(AuthenticatedUser currentUser, Long sessionId, String content, MultipartFile file);

    Resource loadAttachment(AuthenticatedUser currentUser, Long messageId);
}
