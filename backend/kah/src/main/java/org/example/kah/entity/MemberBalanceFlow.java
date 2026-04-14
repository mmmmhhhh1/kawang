package org.example.kah.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * Immutable member balance flow record.
 */
@Data
public class MemberBalanceFlow {

    private Long id;
    private Long userId;
    private String bizType;
    private String bizNo;
    private String direction;
    private BigDecimal amount;
    private BigDecimal balanceBefore;
    private BigDecimal balanceAfter;
    private String remark;
    private LocalDateTime createdAt;
}