package org.example.kah.dto.support;

public record SupportUnreadView(
        Long sessionId,
        Long memberId,
        int memberUnreadCount,
        int adminUnreadCount
) {
}
