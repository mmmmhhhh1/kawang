package org.example.kah.entity;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class ProductAccount {

    private Long id;
    private Long productId;
    private String productTitle;
    private String assignedOrderNo;
    private String accountNameMasked;
    private String accountCiphertext;
    private String secretCiphertext;
    private String noteCiphertext;
    private String accountDigest;
    private String status;
    private String resourceType;
    private String cardKeyCiphertext;
    private String cardKeyDigest;
    private String allocationHandle;
    private String saleStatus;
    private String enableStatus;
    private String usedStatus;
    private Long assignedOrderId;
    private LocalDateTime assignedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}