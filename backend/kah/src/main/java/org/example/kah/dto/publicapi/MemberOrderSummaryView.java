package org.example.kah.dto.publicapi;

import java.math.BigDecimal;

public record MemberOrderSummaryView(
        long orderCount,
        long totalQuantity,
        BigDecimal totalAmount,
        long totalCardKeys
) {
}
