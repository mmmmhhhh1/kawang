package org.example.kah.controller;

import jakarta.validation.Valid;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.example.kah.common.ApiResponse;
import org.example.kah.common.CursorPageResponse;
import org.example.kah.dto.admin.AdminSupportSessionItemView;
import org.example.kah.dto.support.SupportDispatchPayload;
import org.example.kah.dto.support.SupportMessageView;
import org.example.kah.dto.support.SupportSendMessageRequest;
import org.example.kah.dto.support.SupportUnreadView;
import org.example.kah.security.AuthenticatedUser;
import org.example.kah.service.SupportChatService;
import org.example.kah.service.SupportRealtimeService;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/admin/support")
@RequiredArgsConstructor
public class AdminSupportController {

    private final SupportChatService supportChatService;
    private final SupportRealtimeService supportRealtimeService;

    @GetMapping("/sessions")
    public ApiResponse<CursorPageResponse<AdminSupportSessionItemView>> sessions(
            Authentication authentication,
            @RequestParam(defaultValue = "30") int size,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) LocalDateTime updatedAfter,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword) {
        AuthenticatedUser currentUser = (AuthenticatedUser) authentication.getPrincipal();
        if (updatedAfter != null) {
            return ApiResponse.success(supportChatService.listAdminSessionsUpdatedAfter(currentUser, size, updatedAfter, status, keyword));
        }
        return ApiResponse.success(supportChatService.listAdminSessions(currentUser, size, cursor, status, keyword));
    }

    @GetMapping("/sessions/{sessionId}/messages")
    public ApiResponse<CursorPageResponse<SupportMessageView>> messages(
            Authentication authentication,
            @PathVariable Long sessionId,
            @RequestParam(defaultValue = "30") int size,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) String after) {
        AuthenticatedUser currentUser = (AuthenticatedUser) authentication.getPrincipal();
        return ApiResponse.success(supportChatService.listAdminMessages(currentUser, sessionId, size, cursor, after));
    }

    @PostMapping("/sessions/{sessionId}/messages")
    public ApiResponse<SupportDispatchPayload> sendMessage(
            Authentication authentication,
            @PathVariable Long sessionId,
            @Valid @RequestBody SupportSendMessageRequest request) {
        AuthenticatedUser currentUser = (AuthenticatedUser) authentication.getPrincipal();
        SupportDispatchPayload payload = supportChatService.sendAdminMessage(currentUser, sessionId, request.content());
        supportRealtimeService.dispatchSupportMessage(payload);
        return ApiResponse.success(payload);
    }

    @PostMapping(value = "/sessions/{sessionId}/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<SupportDispatchPayload> sendAttachment(
            Authentication authentication,
            @PathVariable Long sessionId,
            @RequestParam(required = false) String content,
            @RequestParam MultipartFile file) {
        AuthenticatedUser currentUser = (AuthenticatedUser) authentication.getPrincipal();
        SupportDispatchPayload payload = supportChatService.sendAdminAttachment(currentUser, sessionId, content, file);
        supportRealtimeService.dispatchSupportMessage(payload);
        return ApiResponse.success(payload);
    }

    @PostMapping("/sessions/{sessionId}/read")
    public ApiResponse<SupportUnreadView> read(Authentication authentication, @PathVariable Long sessionId) {
        AuthenticatedUser currentUser = (AuthenticatedUser) authentication.getPrincipal();
        SupportUnreadView unread = supportChatService.markAdminRead(currentUser, sessionId);
        supportRealtimeService.dispatchUnreadUpdated(unread.memberId(), unread);
        return ApiResponse.success(unread);
    }
}
