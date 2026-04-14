package org.example.kah.entity;

import java.time.LocalDateTime;
import lombok.Data;

/**
 * Payment QR configuration entity.
 */
@Data
public class PaymentQrConfig {

    private Long id;
    private String name;
    private String imagePath;
    private String status;
    private Long createdBy;
    private Long activatedBy;
    private LocalDateTime activatedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String createdByName;
    private String activatedByName;
}