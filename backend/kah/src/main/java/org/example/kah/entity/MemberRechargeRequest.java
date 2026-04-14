package org.example.kah.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * Member recharge request entity.
 */
@Data
public class MemberRechargeRequest {

    private Long id;
    private String requestNo;
    private Long userId;
    private BigDecimal amount;
    private String status;
    private String screenshotPath;
    private String payerRemark;
    private Long reviewedBy;
    private LocalDateTime reviewedAt;
    private String rejectReason;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    private String username;
    private String email;
    private String reviewedByName;
}