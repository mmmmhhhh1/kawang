package org.example.kah.service;

import java.util.List;
import java.util.Map;
import org.example.kah.cache.ProductBaseCacheItem;
import org.example.kah.cache.ProductStatsCacheItem;

/**
 * 商城商品缓存服务接口。
 * 负责商品基础信息缓存、库存销量缓存以及相关缓存重建与失效。
 */
public interface ProductCacheService {

    /** 查询当前前台可售商品的基础信息缓存列表。 */
    List<ProductBaseCacheItem> getActiveProductBases();

    /** 查询单商品基础信息缓存。 */
    ProductBaseCacheItem getActiveProductBase(Long productId);

    /** 批量查询商品统计缓存。 */
    Map<Long, ProductStatsCacheItem> getProductStats(List<Long> productIds);

    /** 查询单商品统计缓存。 */
    ProductStatsCacheItem getProductStats(Long productId);

    /** 根据数据库当前状态刷新单商品基础缓存，并同步重建活动商品列表缓存。 */
    void refreshProductBase(Long productId);

    /** 根据数据库当前状态刷新单商品统计缓存。 */
    void refreshProductStats(Long productId);

    /** 删除单商品基础缓存和活动商品列表缓存。 */
    void evictProductBase(Long productId);

    /** 删除单商品统计缓存。 */
    void evictProductStats(Long productId);

    /** 商品被删除后移除相关缓存。 */
    void removeProduct(Long productId);

    /** 清理全部商品相关缓存。 */
    void clearAllProductCaches();

    /** 预热活动商品基础列表缓存。 */
    void warmupActiveProductBases();
}