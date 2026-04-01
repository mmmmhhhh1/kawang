package org.example.kah.dto.publicapi;

import java.math.BigDecimal;

public record OrderCreatedResponse(
        String orderNo,
        String status,
        Integer quantity,
        BigDecimal totalAmount,
        String message
) {
}
