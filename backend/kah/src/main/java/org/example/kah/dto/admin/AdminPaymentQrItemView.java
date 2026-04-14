package org.example.kah.dto.admin;

import java.time.LocalDateTime;

public record AdminPaymentQrItemView(
        Long id,
        String name,
        String status,
        String imageUrl,
        String createdByName,
        String activatedByName,
        LocalDateTime activatedAt,
        LocalDateTime createdAt
) {
}