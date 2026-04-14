package org.example.kah.dto.publicapi;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record MemberRechargeItemView(
        Long id,
        String requestNo,
        BigDecimal amount,
        String status,
        String payerRemark,
        String rejectReason,
        LocalDateTime reviewedAt,
        LocalDateTime createdAt
) {
}