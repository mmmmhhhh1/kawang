package org.example.kah.dto.publicapi;

import java.time.LocalDateTime;

public record NoticeView(
        Long id,
        String title,
        String summary,
        String content,
        LocalDateTime publishedAt
) {
}
