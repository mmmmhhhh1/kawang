package org.example.kah.cache;

import java.math.BigDecimal;

/**
 * 商城商品基础信息缓存对象。
 * 只承载前台展示商品时变化不频繁的字段，不包含库存与销量。
 */
public record ProductBaseCacheItem(
        Long id,
        String sku,
        String title,
        String vendor,
        String planName,
        String description,
        BigDecimal price,
        String status,
        Integer sortOrder
) {
}