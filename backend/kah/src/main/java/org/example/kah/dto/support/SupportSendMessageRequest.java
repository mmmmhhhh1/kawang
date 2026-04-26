package org.example.kah.dto.support;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record SupportSendMessageRequest(
        @NotNull Long sessionId,
        @NotBlank String content
) {
}
