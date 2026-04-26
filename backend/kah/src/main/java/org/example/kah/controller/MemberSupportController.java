package org.example.kah.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.kah.common.ApiResponse;
import org.example.kah.common.CursorPageResponse;
import org.example.kah.dto.publicapi.MemberSupportSessionView;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/auth/support")
@RequiredArgsConstructor
public class MemberSupportController {

    private final SupportChatService supportChatService;
    private final SupportRealtimeService supportRealtimeService;

    @GetMapping("/session")
    public ApiResponse<MemberSupportSessionView> session(Authentication authentication) {
        AuthenticatedUser currentUser = (AuthenticatedUser) authentication.getPrincipal();
        return ApiResponse.success(supportChatService.getOrCreateMemberSession(currentUser));
    }

    @GetMapping("/messages")
    public ApiResponse<CursorPageResponse<SupportMessageView>> messages(
            Authentication authentication,
            @RequestParam(defaultValue = "30") int size,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) String after) {
        AuthenticatedUser currentUser = (AuthenticatedUser) authentication.getPrincipal();
        return ApiResponse.success(supportChatService.listMemberMessages(currentUser, size, cursor, after));
    }

    @PostMapping("/messages")
    public ApiResponse<SupportDispatchPayload> sendMessage(
            Authentication authentication,
            @Valid @RequestBody SupportSendMessageRequest request) {
        AuthenticatedUser currentUser = (AuthenticatedUser) authentication.getPrincipal();
        SupportDispatchPayload payload = supportChatService.sendMemberMessage(currentUser, request.sessionId(), request.content());
        supportRealtimeService.dispatchSupportMessage(payload);
        return ApiResponse.success(payload);
    }

    @PostMapping(value = "/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<SupportDispatchPayload> sendAttachment(
            Authentication authentication,
            @RequestParam Long sessionId,
            @RequestParam(required = false) String content,
            @RequestParam MultipartFile file) {
        AuthenticatedUser currentUser = (AuthenticatedUser) authentication.getPrincipal();
        SupportDispatchPayload payload = supportChatService.sendMemberAttachment(currentUser, sessionId, content, file);
        supportRealtimeService.dispatchSupportMessage(payload);
        return ApiResponse.success(payload);
    }

    @PostMapping("/read")
    public ApiResponse<SupportUnreadView> read(Authentication authentication) {
        AuthenticatedUser currentUser = (AuthenticatedUser) authentication.getPrincipal();
        SupportUnreadView unread = supportChatService.markMemberRead(currentUser);
        supportRealtimeService.dispatchUnreadUpdated(currentUser.userId(), unread);
        return ApiResponse.success(unread);
    }
}
