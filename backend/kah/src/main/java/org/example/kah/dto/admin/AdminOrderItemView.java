package org.example.kah.dto.admin;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record AdminOrderItemView(
        Long id,
        String orderNo,
        Long productId,
        String productTitleSnapshot,
        Integer quantity,
        BigDecimal unitPrice,
        BigDecimal totalAmount,
        String buyerName,
        String buyerContact,
        String buyerRemark,
        String status,
        String closedReason,
        LocalDateTime createdAt
) {
}
