package org.example.kah.service.impl;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.example.kah.common.BusinessException;
import org.example.kah.common.CursorPageResponse;
import org.example.kah.common.ErrorCode;
import org.example.kah.dto.admin.AdminSupportSessionItemView;
import org.example.kah.dto.publicapi.MemberSupportSessionView;
import org.example.kah.dto.support.SupportDispatchPayload;
import org.example.kah.dto.support.SupportMessageView;
import org.example.kah.dto.support.SupportUnreadView;
import org.example.kah.entity.AdminStatus;
import org.example.kah.entity.AdminUser;
import org.example.kah.entity.SupportMessage;
import org.example.kah.entity.SupportSession;
import org.example.kah.mapper.AdminUserMapper;
import org.example.kah.mapper.SupportMessageMapper;
import org.example.kah.mapper.SupportSessionMapper;
import org.example.kah.security.AuthenticatedUser;
import org.example.kah.security.UserScope;
import org.example.kah.service.FileStorageService;
import org.example.kah.service.SupportChatService;
import org.example.kah.service.impl.base.AbstractServiceSupport;
import org.example.kah.util.CursorCodecUtils;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class SupportChatServiceImpl extends AbstractServiceSupport implements SupportChatService {

    private static final String SESSION_STATUS_OPEN = "OPEN";
    private static final String MESSAGE_TYPE_TEXT = "TEXT";
    private static final String MESSAGE_TYPE_IMAGE = "IMAGE";
    private static final String MESSAGE_TYPE_FILE = "FILE";
    private static final long MAX_ATTACHMENT_SIZE = 1000L * 1024L * 1024L;
    private static final int MAX_ATTACHMENT_NAME_LENGTH = 180;
    private static final int MAX_ATTACHMENT_CONTENT_TYPE_LENGTH = 120;
    private static final Pattern ATTACHMENT_UNSAFE_CHARACTERS = Pattern.compile("[\\\\/:*?\"<>|\\p{Cntrl}]");

    private final SupportSessionMapper supportSessionMapper;
    private final SupportMessageMapper supportMessageMapper;
    private final AdminUserMapper adminUserMapper;
    private final FileStorageService fileStorageService;

    @Override
    @Transactional
    public MemberSupportSessionView getOrCreateMemberSession(AuthenticatedUser currentUser) {
        requireMember(currentUser);
        return toMemberSessionView(requireOrCreateSessionBasic(currentUser.userId()));
    }

    @Override
    @Transactional
    public CursorPageResponse<SupportMessageView> listMemberMessages(AuthenticatedUser currentUser, int size, String cursor, String after) {
        requireMember(currentUser);
        SupportSession session = requireOrCreateSessionBasic(currentUser.userId());
        return loadMessages(session.getId(), size, cursor, after);
    }

    @Override
    @Transactional
    public SupportUnreadView markMemberRead(AuthenticatedUser currentUser) {
        requireMember(currentUser);
        SupportSession session = requireOrCreateSessionBasic(currentUser.userId());
        supportSessionMapper.markMemberRead(session.getId());
        session.setMemberUnreadCount(0);
        session.setUpdatedAt(LocalDateTime.now());
        return toUnreadView(session);
    }

    @Override
    @Transactional(readOnly = true)
    public CursorPageResponse<AdminSupportSessionItemView> listAdminSessions(
            AuthenticatedUser currentUser,
            int size,
            String cursor,
            String status,
            String keyword) {
        requireAdmin(currentUser);
        int safeSize = normalizeSize(size, 50);
        CursorCodecUtils.DecodedCursor decodedCursor = CursorCodecUtils.decode(cursor);
        HashMap<String, Object> params = new HashMap<>();
        params.put("status", trim(status));
        params.put("keyword", trim(keyword));
        params.put("limit", safeSize + 1);
        if (decodedCursor != null) {
            params.put("cursorCreatedAt", decodedCursor.createdAt());
            params.put("cursorId", decodedCursor.id());
        }
        List<SupportSession> rows = supportSessionMapper.findAdminCursorPage(params);
        boolean hasMore = rows.size() > safeSize;
        List<SupportSession> pageItems = hasMore ? rows.subList(0, safeSize) : rows;
        String nextCursor = hasMore
                ? CursorCodecUtils.encode(pageItems.get(pageItems.size() - 1).getSortTime(), pageItems.get(pageItems.size() - 1).getId())
                : null;
        return new CursorPageResponse<>(pageItems.stream().map(this::toAdminSessionView).toList(), nextCursor, hasMore);
    }

    @Override
    @Transactional(readOnly = true)
    public CursorPageResponse<AdminSupportSessionItemView> listAdminSessionsUpdatedAfter(
            AuthenticatedUser currentUser,
            int size,
            LocalDateTime updatedAfter,
            String status,
            String keyword) {
        requireAdmin(currentUser);
        require(updatedAfter != null, ErrorCode.BAD_REQUEST, "updatedAfter is required");
        int safeSize = normalizeSize(size, 100);
        HashMap<String, Object> params = new HashMap<>();
        params.put("updatedAfter", updatedAfter);
        params.put("status", trim(status));
        params.put("keyword", trim(keyword));
        params.put("limit", safeSize + 1);
        List<SupportSession> rows = supportSessionMapper.findAdminUpdatedAfter(params);
        boolean hasMore = rows.size() > safeSize;
        List<SupportSession> pageItems = hasMore ? rows.subList(0, safeSize) : rows;
        String nextCursor = hasMore
                ? CursorCodecUtils.encode(pageItems.get(pageItems.size() - 1).getUpdatedAt(), pageItems.get(pageItems.size() - 1).getId())
                : null;
        return new CursorPageResponse<>(pageItems.stream().map(this::toAdminSessionView).toList(), nextCursor, hasMore);
    }

    @Override
    @Transactional(readOnly = true)
    public CursorPageResponse<SupportMessageView> listAdminMessages(
            AuthenticatedUser currentUser,
            Long sessionId,
            int size,
            String cursor,
            String after) {
        requireAdmin(currentUser);
        requireSessionBasic(sessionId);
        return loadMessages(sessionId, size, cursor, after);
    }

    @Override
    @Transactional
    public SupportUnreadView markAdminRead(AuthenticatedUser currentUser, Long sessionId) {
        requireAdmin(currentUser);
        SupportSession session = requireSessionBasic(sessionId);
        supportSessionMapper.markAdminRead(sessionId);
        session.setAdminUnreadCount(0);
        session.setUpdatedAt(LocalDateTime.now());
        return toUnreadView(session);
    }

    @Override
    @Transactional
    public SupportDispatchPayload sendMemberMessage(AuthenticatedUser currentUser, Long sessionId, String content) {
        SupportSession session = requireMemberSession(currentUser, sessionId);
        SupportMessage persisted = createTextMessage(session.getId(), UserScope.MEMBER, currentUser.userId(), content);
        applyInMemorySessionState(session, UserScope.MEMBER, buildPreview(persisted), persisted.getCreatedAt());
        return buildDispatchPayload(session, persisted);
    }

    @Override
    @Transactional
    public SupportDispatchPayload sendAdminMessage(AuthenticatedUser currentUser, Long sessionId, String content) {
        SupportSession session = requireAdminSession(currentUser, sessionId);
        SupportMessage persisted = createTextMessage(session.getId(), UserScope.ADMIN, currentUser.userId(), content);
        applyInMemorySessionState(session, UserScope.ADMIN, buildPreview(persisted), persisted.getCreatedAt());
        return buildDispatchPayload(session, persisted);
    }

    @Override
    @Transactional
    public SupportDispatchPayload sendMemberAttachment(AuthenticatedUser currentUser, Long sessionId, String content, MultipartFile file) {
        SupportSession session = requireMemberSession(currentUser, sessionId);
        SupportMessage persisted = createAttachmentMessage(session.getId(), UserScope.MEMBER, currentUser.userId(), content, file);
        applyInMemorySessionState(session, UserScope.MEMBER, buildPreview(persisted), persisted.getCreatedAt());
        return buildDispatchPayload(session, persisted);
    }

    @Override
    @Transactional
    public SupportDispatchPayload sendAdminAttachment(AuthenticatedUser currentUser, Long sessionId, String content, MultipartFile file) {
        SupportSession session = requireAdminSession(currentUser, sessionId);
        SupportMessage persisted = createAttachmentMessage(session.getId(), UserScope.ADMIN, currentUser.userId(), content, file);
        applyInMemorySessionState(session, UserScope.ADMIN, buildPreview(persisted), persisted.getCreatedAt());
        return buildDispatchPayload(session, persisted);
    }

    @Override
    @Transactional(readOnly = true)
    public Resource loadAttachment(AuthenticatedUser currentUser, Long messageId) {
        require(currentUser != null, ErrorCode.UNAUTHORIZED, "login required");
        SupportMessage message = requireMessage(messageId);
        require(message.getAttachmentPath() != null && !message.getAttachmentPath().isBlank(), ErrorCode.NOT_FOUND, "attachment not found");

        SupportSession session = requireSessionBasic(message.getSessionId());
        if (UserScope.MEMBER.equals(currentUser.scope())) {
            require(session.getMemberId().equals(currentUser.userId()), ErrorCode.FORBIDDEN, "attachment forbidden");
        } else {
            requireAdmin(currentUser);
        }
        return fileStorageService.loadAsResource(message.getAttachmentPath());
    }

    private CursorPageResponse<SupportMessageView> loadMessages(Long sessionId, int size, String cursor, String after) {
        int safeSize = normalizeSize(size, 50);
        CursorCodecUtils.DecodedCursor decodedCursor = CursorCodecUtils.decode(cursor);
        CursorCodecUtils.DecodedCursor decodedAfter = CursorCodecUtils.decode(after);
        require(decodedCursor == null || decodedAfter == null, ErrorCode.BAD_REQUEST, "cursor and after cannot be used together");
        HashMap<String, Object> params = new HashMap<>();
        params.put("sessionId", sessionId);
        params.put("limit", safeSize + 1);
        if (decodedCursor != null) {
            params.put("cursorCreatedAt", decodedCursor.createdAt());
            params.put("cursorId", decodedCursor.id());
            List<SupportMessage> rows = supportMessageMapper.findSessionCursorPage(params);
            boolean hasMore = rows.size() > safeSize;
            List<SupportMessage> pageItems = hasMore ? rows.subList(0, safeSize) : rows;
            String nextCursor = hasMore
                    ? CursorCodecUtils.encode(pageItems.get(pageItems.size() - 1).getCreatedAt(), pageItems.get(pageItems.size() - 1).getId())
                    : null;
            List<SupportMessage> ordered = new ArrayList<>(pageItems);
            java.util.Collections.reverse(ordered);
            return new CursorPageResponse<>(ordered.stream().map(this::toMessageView).toList(), nextCursor, hasMore);
        }
        if (decodedAfter != null) {
            params.put("afterCreatedAt", decodedAfter.createdAt());
            params.put("afterId", decodedAfter.id());
            List<SupportMessage> rows = supportMessageMapper.findSessionAfterCursorPage(params);
            boolean hasMore = rows.size() > safeSize;
            List<SupportMessage> pageItems = hasMore ? rows.subList(0, safeSize) : rows;
            String nextCursor = hasMore
                    ? CursorCodecUtils.encode(pageItems.get(pageItems.size() - 1).getCreatedAt(), pageItems.get(pageItems.size() - 1).getId())
                    : null;
            return new CursorPageResponse<>(pageItems.stream().map(this::toMessageView).toList(), nextCursor, hasMore);
        }
        List<SupportMessage> rows = supportMessageMapper.findSessionCursorPage(params);
        boolean hasMore = rows.size() > safeSize;
        List<SupportMessage> pageItems = hasMore ? rows.subList(0, safeSize) : rows;
        String nextCursor = hasMore
                ? CursorCodecUtils.encode(pageItems.get(pageItems.size() - 1).getCreatedAt(), pageItems.get(pageItems.size() - 1).getId())
                : null;
        List<SupportMessage> ordered = new ArrayList<>(pageItems);
        java.util.Collections.reverse(ordered);
        return new CursorPageResponse<>(ordered.stream().map(this::toMessageView).toList(), nextCursor, hasMore);
    }

    private SupportDispatchPayload buildDispatchPayload(SupportSession session, SupportMessage message) {
        return new SupportDispatchPayload(
                toMessageView(message),
                toAdminSessionView(session),
                toMemberSessionView(session),
                toUnreadView(session));
    }

    private SupportMessage createTextMessage(Long sessionId, String senderScope, Long senderId, String content) {
        LocalDateTime now = LocalDateTime.now();
        SupportMessage message = new SupportMessage();
        message.setSessionId(sessionId);
        message.setSenderScope(senderScope);
        message.setSenderId(senderId);
        message.setMessageType(MESSAGE_TYPE_TEXT);
        message.setContent(normalizeTextContent(content));
        message.setCreatedAt(now);
        supportMessageMapper.insert(message);
        updateSessionState(sessionId, senderScope, buildPreview(message), now);
        return message;
    }

    private SupportMessage createAttachmentMessage(Long sessionId, String senderScope, Long senderId, String content, MultipartFile file) {
        validateAttachment(file);

        LocalDateTime now = LocalDateTime.now();
        SupportMessage message = new SupportMessage();
        message.setSessionId(sessionId);
        message.setSenderScope(senderScope);
        message.setSenderId(senderId);
        message.setMessageType(resolveAttachmentMessageType(file));
        message.setContent(normalizeOptionalContent(content));
        message.setAttachmentPath(fileStorageService.saveSupportAttachment(file));
        message.setAttachmentName(resolveAttachmentName(file));
        message.setAttachmentContentType(sanitizeAttachmentContentType(file.getContentType()));
        message.setAttachmentSize(file.getSize());
        message.setCreatedAt(now);
        supportMessageMapper.insert(message);
        updateSessionState(sessionId, senderScope, buildPreview(message), now);
        return message;
    }

    private void updateSessionState(Long sessionId, String senderScope, String preview, LocalDateTime createdAt) {
        if (UserScope.MEMBER.equals(senderScope)) {
            supportSessionMapper.applyMemberMessage(sessionId, preview, createdAt);
        } else {
            supportSessionMapper.applyAdminMessage(sessionId, preview, createdAt);
        }
    }

    private void applyInMemorySessionState(SupportSession session, String senderScope, String preview, LocalDateTime createdAt) {
        session.setLastMessagePreview(preview);
        session.setLastMessageAt(createdAt);
        session.setSortTime(createdAt);
        session.setUpdatedAt(createdAt);
        if (UserScope.MEMBER.equals(senderScope)) {
            session.setAdminUnreadCount(safeCount(session.getAdminUnreadCount()) + 1);
        } else {
            session.setMemberUnreadCount(safeCount(session.getMemberUnreadCount()) + 1);
            session.setAdminUnreadCount(0);
        }
    }

    private SupportSession requireMemberSession(AuthenticatedUser currentUser, Long sessionId) {
        requireMember(currentUser);
        SupportSession session = requireOrCreateSessionWithMember(currentUser.userId());
        require(session.getId().equals(sessionId), ErrorCode.FORBIDDEN, "session forbidden");
        require(SESSION_STATUS_OPEN.equals(session.getStatus()), ErrorCode.BAD_REQUEST, "session is closed");
        return session;
    }

    private SupportSession requireAdminSession(AuthenticatedUser currentUser, Long sessionId) {
        requireAdmin(currentUser);
        SupportSession session = requireSessionWithMember(sessionId);
        require(SESSION_STATUS_OPEN.equals(session.getStatus()), ErrorCode.BAD_REQUEST, "session is closed");
        return session;
    }

    private SupportSession requireOrCreateSessionBasic(Long memberId) {
        SupportSession session = supportSessionMapper.findByMemberIdBasic(memberId);
        if (session != null) {
            return session;
        }
        supportSessionMapper.insertIgnore(memberId);
        return requireSessionByMemberBasic(memberId);
    }

    private SupportSession requireOrCreateSessionWithMember(Long memberId) {
        SupportSession session = supportSessionMapper.findByMemberIdWithMember(memberId);
        if (session != null) {
            return session;
        }
        supportSessionMapper.insertIgnore(memberId);
        return requireSessionByMemberWithMember(memberId);
    }

    private SupportSession requireSessionByMemberBasic(Long memberId) {
        SupportSession session = supportSessionMapper.findByMemberIdBasic(memberId);
        if (session == null) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "support session init failed");
        }
        return session;
    }

    private SupportSession requireSessionByMemberWithMember(Long memberId) {
        SupportSession session = supportSessionMapper.findByMemberIdWithMember(memberId);
        if (session == null) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "support session init failed");
        }
        return session;
    }

    private SupportSession requireSessionBasic(Long sessionId) {
        SupportSession session = supportSessionMapper.findById(sessionId);
        if (session == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "support session not found");
        }
        return session;
    }

    private SupportSession requireSessionWithMember(Long sessionId) {
        SupportSession session = supportSessionMapper.findByIdWithMember(sessionId);
        if (session == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "support session not found");
        }
        return session;
    }

    private SupportMessage requireMessage(Long messageId) {
        SupportMessage message = supportMessageMapper.findByIdWithSession(messageId);
        if (message == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "support message not found");
        }
        return message;
    }

    private void requireMember(AuthenticatedUser currentUser) {
        require(currentUser != null && UserScope.MEMBER.equals(currentUser.scope()), ErrorCode.UNAUTHORIZED, "member login required");
    }

    private void requireAdmin(AuthenticatedUser currentUser) {
        require(currentUser != null && UserScope.ADMIN.equals(currentUser.scope()), ErrorCode.UNAUTHORIZED, "admin login required");
        AdminUser adminUser = adminUserMapper.findById(currentUser.userId());
        require(adminUser != null, ErrorCode.UNAUTHORIZED, "admin login required");
        require(AdminStatus.ACTIVE.equals(adminUser.getStatus()), ErrorCode.FORBIDDEN, "admin disabled");
    }

    private void validateAttachment(MultipartFile file) {
        require(file != null && !file.isEmpty(), ErrorCode.BAD_REQUEST, "attachment required");
        require(file.getSize() > 0 && file.getSize() <= MAX_ATTACHMENT_SIZE, ErrorCode.BAD_REQUEST, "attachment too large");
    }

    private String normalizeTextContent(String content) {
        String trimmed = trim(content);
        require(trimmed != null && !trimmed.isBlank(), ErrorCode.BAD_REQUEST, "message content required");
        require(trimmed.length() <= 1000, ErrorCode.BAD_REQUEST, "message content too long");
        return trimmed;
    }

    private String normalizeOptionalContent(String content) {
        String trimmed = trim(content);
        if (trimmed == null || trimmed.isBlank()) {
            return "";
        }
        require(trimmed.length() <= 1000, ErrorCode.BAD_REQUEST, "message content too long");
        return trimmed;
    }

    private String resolveAttachmentMessageType(MultipartFile file) {
        String contentType = trim(file.getContentType());
        if (contentType != null && contentType.toLowerCase(Locale.ROOT).startsWith("image/")) {
            return MESSAGE_TYPE_IMAGE;
        }
        return MESSAGE_TYPE_FILE;
    }

    private String resolveAttachmentName(MultipartFile file) {
        String fallback = MESSAGE_TYPE_IMAGE.equals(resolveAttachmentMessageType(file)) ? "image" : "file";
        String originalFilename = trim(file.getOriginalFilename());
        if (originalFilename == null || originalFilename.isBlank()) {
            return fallback;
        }

        String normalized = originalFilename.replace('\\', '/');
        int separatorIndex = normalized.lastIndexOf('/');
        if (separatorIndex >= 0) {
            normalized = normalized.substring(separatorIndex + 1);
        }

        normalized = ATTACHMENT_UNSAFE_CHARACTERS.matcher(normalized).replaceAll("_").trim();
        normalized = stripUnsupportedSupplementaryCharacters(normalized);
        normalized = normalized.replaceAll("\\s+", " ");

        if (normalized.isBlank()) {
            return fallback;
        }

        int extensionIndex = normalized.lastIndexOf('.');
        String extension = extensionIndex > 0 ? normalized.substring(extensionIndex) : "";
        String baseName = extensionIndex > 0 ? normalized.substring(0, extensionIndex) : normalized;
        extension = extension.length() > 16 ? "" : extension;

        int allowedBaseLength = Math.max(1, MAX_ATTACHMENT_NAME_LENGTH - extension.length());
        if (baseName.length() > allowedBaseLength) {
            baseName = baseName.substring(0, allowedBaseLength);
        }

        String sanitized = (baseName + extension).replaceAll("[.\\s]+$", "").trim();
        return sanitized.isBlank() ? fallback : sanitized;
    }

    private String sanitizeAttachmentContentType(String contentType) {
        String trimmed = trim(contentType);
        if (trimmed == null || trimmed.isBlank()) {
            return null;
        }
        String normalized = stripUnsupportedSupplementaryCharacters(trimmed);
        normalized = normalized.replaceAll("\\p{Cntrl}", "");
        if (normalized.length() > MAX_ATTACHMENT_CONTENT_TYPE_LENGTH) {
            normalized = normalized.substring(0, MAX_ATTACHMENT_CONTENT_TYPE_LENGTH);
        }
        return normalized.isBlank() ? null : normalized;
    }

    private String stripUnsupportedSupplementaryCharacters(String value) {
        StringBuilder builder = new StringBuilder(value.length());
        value.codePoints()
                .filter(codePoint -> !Character.isISOControl(codePoint))
                .filter(codePoint -> codePoint <= Character.MAX_VALUE)
                .forEach(builder::appendCodePoint);
        return builder.toString();
    }

    private String buildPreview(SupportMessage message) {
        if (MESSAGE_TYPE_TEXT.equals(message.getMessageType())) {
            return buildTextPreview(message.getContent());
        }
        String label = MESSAGE_TYPE_IMAGE.equals(message.getMessageType()) ? "[Image]" : "[File]";
        String attachmentName = trim(message.getAttachmentName());
        String suffix = attachmentName == null ? "" : " " + attachmentName;
        String caption = trim(message.getContent());
        if (caption != null && !caption.isBlank()) {
            return trimPreview(label + suffix + " " + caption);
        }
        return trimPreview(label + suffix);
    }

    private String buildTextPreview(String content) {
        return trimPreview(content);
    }

    private String trimPreview(String content) {
        String normalized = content == null ? "" : content.replace('\r', ' ').replace('\n', ' ').trim();
        if (normalized.length() <= 120) {
            return normalized;
        }
        return normalized.substring(0, 120);
    }

    private SupportMessageView toMessageView(SupportMessage message) {
        return new SupportMessageView(
                message.getId(),
                message.getSessionId(),
                message.getSenderScope(),
                message.getSenderId(),
                message.getMessageType(),
                message.getContent(),
                message.getAttachmentName(),
                message.getAttachmentContentType(),
                message.getAttachmentSize(),
                message.getAttachmentPath() == null ? null : "/api/support/messages/" + message.getId() + "/attachment",
                message.getCreatedAt());
    }

    private MemberSupportSessionView toMemberSessionView(SupportSession session) {
        return new MemberSupportSessionView(
                session.getId(),
                session.getStatus(),
                session.getLastMessagePreview(),
                session.getLastMessageAt(),
                safeCount(session.getMemberUnreadCount()),
                safeCount(session.getAdminUnreadCount()),
                session.getCreatedAt(),
                session.getUpdatedAt());
    }

    private AdminSupportSessionItemView toAdminSessionView(SupportSession session) {
        return new AdminSupportSessionItemView(
                session.getId(),
                session.getMemberId(),
                session.getMemberUsername(),
                session.getMemberEmail(),
                session.getStatus(),
                session.getLastMessagePreview(),
                session.getLastMessageAt(),
                safeCount(session.getMemberUnreadCount()),
                safeCount(session.getAdminUnreadCount()),
                session.getCreatedAt(),
                session.getUpdatedAt());
    }

    private SupportUnreadView toUnreadView(SupportSession session) {
        return new SupportUnreadView(
                session.getId(),
                session.getMemberId(),
                safeCount(session.getMemberUnreadCount()),
                safeCount(session.getAdminUnreadCount()));
    }

    private int safeCount(Integer value) {
        return value == null ? 0 : value;
    }
}
