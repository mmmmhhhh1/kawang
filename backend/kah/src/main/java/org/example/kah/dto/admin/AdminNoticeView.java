package org.example.kah.dto.admin;

import java.time.LocalDateTime;

public record AdminNoticeView(
        Long id,
        String title,
        String summary,
        String content,
        String status,
        Integer sortOrder,
        LocalDateTime publishedAt,
        LocalDateTime updatedAt
) {
}
