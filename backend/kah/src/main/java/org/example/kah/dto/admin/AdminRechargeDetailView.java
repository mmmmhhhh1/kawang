package org.example.kah.dto.admin;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record AdminRechargeDetailView(
        Long id,
        String requestNo,
        Long userId,
        String username,
        String email,
        BigDecimal amount,
        String status,
        String payerRemark,
        String rejectReason,
        String screenshotUrl,
        String reviewedByName,
        LocalDateTime createdAt,
        LocalDateTime reviewedAt
) {
}