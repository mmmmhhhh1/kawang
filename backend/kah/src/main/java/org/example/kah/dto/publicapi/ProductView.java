package org.example.kah.dto.publicapi;

import java.math.BigDecimal;

public record ProductView(
        Long id,
        String sku,
        String title,
        String vendor,
        String planName,
        String description,
        BigDecimal price,
        Integer availableStock,
        Integer soldCount
) {
}
