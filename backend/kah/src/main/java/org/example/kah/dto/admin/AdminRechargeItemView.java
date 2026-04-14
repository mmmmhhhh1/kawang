package org.example.kah.dto.admin;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record AdminRechargeItemView(
        Long id,
        String requestNo,
        Long userId,
        String username,
        String email,
        BigDecimal amount,
        String status,
        String payerRemark,
        LocalDateTime createdAt,
        LocalDateTime reviewedAt
) {
}