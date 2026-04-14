package org.example.kah.dto.admin;

import java.time.LocalDateTime;

public record AdminNotificationEvent(
        String type,
        String title,
        String message,
        Long requestId,
        LocalDateTime createdAt
) {
}