package org.example.kah.dto.admin;

import java.time.LocalDateTime;

public record AdminSupportSessionItemView(
        Long id,
        Long memberId,
        String memberUsername,
        String memberEmail,
        String status,
        String lastMessagePreview,
        LocalDateTime lastMessageAt,
        int memberUnreadCount,
        int adminUnreadCount,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
