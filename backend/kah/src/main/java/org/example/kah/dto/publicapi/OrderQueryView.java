package org.example.kah.dto.publicapi;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record OrderQueryView(
        Long id,
        String orderNo,
        String productTitle,
        Integer quantity,
        BigDecimal totalAmount,
        String status,
        LocalDateTime createdAt
) {
}
