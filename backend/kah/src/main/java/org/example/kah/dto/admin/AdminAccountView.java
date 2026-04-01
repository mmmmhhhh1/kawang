package org.example.kah.dto.admin;

import java.time.LocalDateTime;

public record AdminAccountView(
        Long id,
        Long productId,
        String productTitle,
        String accountNameMasked,
        String status,
        Long assignedOrderId,
        LocalDateTime assignedAt,
        LocalDateTime createdAt
) {
}
