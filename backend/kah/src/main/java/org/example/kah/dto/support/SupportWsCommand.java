package org.example.kah.dto.support;

public record SupportWsCommand(
        String type,
        Long sessionId,
        String content
) {
}
