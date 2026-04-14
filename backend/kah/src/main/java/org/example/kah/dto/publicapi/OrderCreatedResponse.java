package org.example.kah.dto.publicapi;

import java.math.BigDecimal;
import java.util.List;

/**
 * Order creation response.
 */
public record OrderCreatedResponse(
        String orderNo,
        String status,
        Integer quantity,
        BigDecimal totalAmount,
        String message,
        List<CardKeyView> cardKeys,
        BigDecimal remainingBalance
) {
}