package org.example.kah.dto.admin;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record AdminProductView(
        Long id,
        String sku,
        String title,
        String vendor,
        String planName,
        String description,
        BigDecimal price,
        Integer availableStock,
        Integer soldCount,
        String status,
        Integer sortOrder,
        LocalDateTime updatedAt
) {
}
