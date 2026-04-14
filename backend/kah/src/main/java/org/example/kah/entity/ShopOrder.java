package org.example.kah.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * Order entity.
 */
@Data
public class ShopOrder {

    private Long id;
    private String orderNo;
    private Long userId;
    private Long productId;
    private String productTitleSnapshot;
    private Integer quantity;
    private BigDecimal unitPrice;
    private BigDecimal totalAmount;
    private BigDecimal balanceAmount;
    private String paymentMethod;
    private String buyerName;
    private String buyerContact;
    private String lookupHash;
    private String buyerRemark;
    private String status;
    private String closedReason;
    private LocalDateTime closedAt;
    private LocalDateTime refundedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}