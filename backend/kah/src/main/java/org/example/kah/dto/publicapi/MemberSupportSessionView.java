package org.example.kah.dto.publicapi;

import java.time.LocalDateTime;

public record MemberSupportSessionView(
        Long id,
        String status,
        String lastMessagePreview,
        LocalDateTime lastMessageAt,
        int memberUnreadCount,
        int adminUnreadCount,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
