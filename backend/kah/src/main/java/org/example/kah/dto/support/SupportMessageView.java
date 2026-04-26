package org.example.kah.dto.support;

import java.time.LocalDateTime;

public record SupportMessageView(
        Long id,
        Long sessionId,
        String senderScope,
        Long senderId,
        String messageType,
        String content,
        String attachmentName,
        String attachmentContentType,
        Long attachmentSize,
        String attachmentUrl,
        LocalDateTime createdAt
) {
}
