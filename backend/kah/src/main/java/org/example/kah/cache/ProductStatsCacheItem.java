package org.example.kah.cache;

/**
 * 商城商品库存与销量缓存对象。
 * 仅承载高频变化的统计字段，方便和基础信息缓存拆分管理。
 */
public record ProductStatsCacheItem(
        Long productId,
        Integer availableStock,
        Integer soldCount
) {
}