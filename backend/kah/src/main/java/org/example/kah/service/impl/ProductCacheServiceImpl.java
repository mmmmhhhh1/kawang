package org.example.kah.service.impl;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import org.example.kah.cache.ProductBaseCacheItem;
import org.example.kah.cache.ProductCacheCodec;
import org.example.kah.cache.ProductCacheConstants;
import org.example.kah.cache.ProductStatsCacheItem;
import org.example.kah.entity.ProductStatus;
import org.example.kah.entity.ShopProduct;
import org.example.kah.mapper.ProductMapper;
import org.example.kah.service.DistributedLockService;
import org.example.kah.service.ProductCacheService;
import org.example.kah.util.CacheTtlUtils;
import org.example.kah.util.LongIdUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * {@link ProductCacheService} 的默认实现。
 * 把商品基础信息缓存、库存销量缓存、缓存重建互斥和回源逻辑统一收口，便于上层服务只关心业务流程。
 */
@Service
@RequiredArgsConstructor
public class ProductCacheServiceImpl implements ProductCacheService {

    private static final Logger log = LoggerFactory.getLogger(ProductCacheServiceImpl.class);

    private final StringRedisTemplate stringRedisTemplate;
    private final ProductCacheCodec productCacheCodec;
    private final ProductMapper productMapper;
    private final DistributedLockService distributedLockService;

    /** 读取当前前台可售商品的基础缓存列表，缓存 miss 时互斥回源数据库。 */
    @Override
    public List<ProductBaseCacheItem> getActiveProductBases() {
        try {
            String cached = stringRedisTemplate.opsForValue().get(ProductCacheConstants.ACTIVE_PRODUCT_BASE_LIST_KEY);
            if (cached != null) {
                return parseCachedValue(ProductCacheConstants.ACTIVE_PRODUCT_BASE_LIST_KEY, cached, productCacheCodec::parseBaseList);
            }
            return rebuildActiveBaseListWithMutex();
        } catch (Exception exception) {
            log.warn("读取活动商品基础缓存失败，回退数据库查询", exception);
            return loadActiveProductBasesFromDb();
        }
    }

    /** 读取单商品基础缓存，商品不存在或已下架时返回空。 */
    @Override
    public ProductBaseCacheItem getActiveProductBase(Long productId) {
        if (!LongIdUtils.isPositive(productId)) {
            return null;
        }
        try {
            String cached = stringRedisTemplate.opsForValue().get(ProductCacheConstants.baseDetailKey(productId));
            if (cached != null) {
                return parseCachedValue(ProductCacheConstants.baseDetailKey(productId), cached, productCacheCodec::parseBaseDetail);
            }
            return rebuildBaseDetailWithMutex(productId);
        } catch (Exception exception) {
            log.warn("读取商品基础缓存失败，回退数据库查询，productId={}", productId, exception);
            return loadActiveProductBaseFromDb(productId);
        }
    }

    /** 批量读取商品统计缓存，缺失部分按商品粒度回源并补齐缓存。 */
    @Override
    public Map<Long, ProductStatsCacheItem> getProductStats(List<Long> productIds) {
        List<Long> normalizedIds = LongIdUtils.normalizeDistinctPositiveIds(productIds);
        if (normalizedIds.isEmpty()) {
            return Map.of();
        }

        try {
            List<String> keys = normalizedIds.stream().map(ProductCacheConstants::statsKey).toList();
            List<String> cachedValues = stringRedisTemplate.opsForValue().multiGet(keys);
            Map<Long, ProductStatsCacheItem> result = new LinkedHashMap<>();
            List<Long> missingIds = new ArrayList<>();
            for (int index = 0; index < normalizedIds.size(); index++) {
                Long productId = normalizedIds.get(index);
                String key = ProductCacheConstants.statsKey(productId);
                String cached = cachedValues == null ? null : cachedValues.get(index);
                if (cached == null) {
                    missingIds.add(productId);
                    continue;
                }
                ProductStatsCacheItem item = parseCachedValue(key, cached, productCacheCodec::parseStats);
                if (item == null) {
                    missingIds.add(productId);
                } else {
                    result.put(productId, item);
                }
            }
            for (Long missingId : missingIds) {
                ProductStatsCacheItem item = getProductStats(missingId);
                if (item != null) {
                    result.put(missingId, item);
                }
            }
            return result;
        } catch (Exception exception) {
            log.warn("批量读取商品统计缓存失败，回退数据库查询", exception);
            return loadStatsMapFromDb(normalizedIds);
        }
    }

    /** 读取单商品统计缓存，缓存 miss 时互斥回源数据库。 */
    @Override
    public ProductStatsCacheItem getProductStats(Long productId) {
        if (!LongIdUtils.isPositive(productId)) {
            return null;
        }
        try {
            String key = ProductCacheConstants.statsKey(productId);
            String cached = stringRedisTemplate.opsForValue().get(key);
            if (cached != null) {
                return parseCachedValue(key, cached, productCacheCodec::parseStats);
            }
            return rebuildStatsWithMutex(productId);
        } catch (Exception exception) {
            log.warn("读取商品统计缓存失败，回退数据库查询，productId={}", productId, exception);
            return loadProductStatsFromDb(productId);
        }
    }

    /** 根据数据库实时状态刷新单商品基础缓存和活动商品列表缓存。 */
    @Override
    public void refreshProductBase(Long productId) {
        if (!LongIdUtils.isPositive(productId)) {
            return;
        }
        ShopProduct product = productMapper.findById(productId);
        if (product == null || !ProductStatus.ACTIVE.equals(product.getStatus())) {
            safeSet(ProductCacheConstants.baseDetailKey(productId), ProductCacheConstants.NULL_MARKER, nullCacheTtl());
            evictProductStats(productId);
        } else {
            safeSet(
                    ProductCacheConstants.baseDetailKey(productId),
                    productCacheCodec.toJson(toBaseItem(product)),
                    baseCacheTtl());
        }
        safeSet(
                ProductCacheConstants.ACTIVE_PRODUCT_BASE_LIST_KEY,
                productCacheCodec.toJson(loadActiveProductBasesFromDb()),
                baseCacheTtl());
    }

    /** 根据数据库实时状态刷新单商品库存和销量缓存。 */
    @Override
    public void refreshProductStats(Long productId) {
        if (!LongIdUtils.isPositive(productId)) {
            return;
        }
        ProductStatsCacheItem item = loadProductStatsFromDb(productId);
        if (item == null) {
            safeSet(ProductCacheConstants.statsKey(productId), ProductCacheConstants.NULL_MARKER, nullCacheTtl());
            return;
        }
        safeSet(ProductCacheConstants.statsKey(productId), productCacheCodec.toJson(item), statsCacheTtl());
    }

    /** 删除单商品基础缓存与活动商品列表缓存。 */
    @Override
    public void evictProductBase(Long productId) {
        if (!LongIdUtils.isPositive(productId)) {
            return;
        }
        safeDelete(ProductCacheConstants.baseDetailKey(productId), ProductCacheConstants.ACTIVE_PRODUCT_BASE_LIST_KEY);
    }

    /** 删除单商品统计缓存。 */
    @Override
    public void evictProductStats(Long productId) {
        if (!LongIdUtils.isPositive(productId)) {
            return;
        }
        safeDelete(ProductCacheConstants.statsKey(productId));
    }

    /** 商品被删除后写入空标记并清理统计缓存，避免前台继续命中旧数据。 */
    @Override
    public void removeProduct(Long productId) {
        if (!LongIdUtils.isPositive(productId)) {
            return;
        }
        safeSet(ProductCacheConstants.baseDetailKey(productId), ProductCacheConstants.NULL_MARKER, nullCacheTtl());
        safeDelete(ProductCacheConstants.statsKey(productId));
        safeSet(
                ProductCacheConstants.ACTIVE_PRODUCT_BASE_LIST_KEY,
                productCacheCodec.toJson(loadActiveProductBasesFromDb()),
                baseCacheTtl());
    }

    /** 清理全部商品相关缓存。 */
    @Override
    public void clearAllProductCaches() {
        try {
            Set<String> keys = stringRedisTemplate.keys(ProductCacheConstants.PRODUCT_CACHE_KEY_PREFIX + "*");
            if (keys != null && !keys.isEmpty()) {
                stringRedisTemplate.delete(keys);
            }
        } catch (Exception exception) {
            log.warn("清理全部商品缓存失败", exception);
        }
    }

    /** 预热当前活动商品基础列表缓存。 */
    @Override
    public void warmupActiveProductBases() {
        safeSet(
                ProductCacheConstants.ACTIVE_PRODUCT_BASE_LIST_KEY,
                productCacheCodec.toJson(loadActiveProductBasesFromDb()),
                baseCacheTtl());
    }

    /** 使用互斥锁重建活动商品基础列表缓存，避免热点 key 击穿。 */
    private List<ProductBaseCacheItem> rebuildActiveBaseListWithMutex() {
        String token = tryAcquireCacheRebuildLock(ProductCacheConstants.ACTIVE_PRODUCT_BASE_LIST_LOCK_KEY);
        if (token != null) {
            try {
                String rechecked = stringRedisTemplate.opsForValue().get(ProductCacheConstants.ACTIVE_PRODUCT_BASE_LIST_KEY);
                if (rechecked != null) {
                    return parseCachedValue(ProductCacheConstants.ACTIVE_PRODUCT_BASE_LIST_KEY, rechecked, productCacheCodec::parseBaseList);
                }
                List<ProductBaseCacheItem> loaded = loadActiveProductBasesFromDb();
                safeSet(ProductCacheConstants.ACTIVE_PRODUCT_BASE_LIST_KEY, productCacheCodec.toJson(loaded), baseCacheTtl());
                return loaded;
            } finally {
                distributedLockService.release(ProductCacheConstants.ACTIVE_PRODUCT_BASE_LIST_LOCK_KEY, token);
            }
        }

        List<ProductBaseCacheItem> waited = waitForCacheValue(
                ProductCacheConstants.ACTIVE_PRODUCT_BASE_LIST_KEY,
                cached -> parseCachedValue(ProductCacheConstants.ACTIVE_PRODUCT_BASE_LIST_KEY, cached, productCacheCodec::parseBaseList));
        return waited != null ? waited : loadActiveProductBasesFromDb();
    }

    /** 使用互斥锁重建单商品基础缓存。 */
    private ProductBaseCacheItem rebuildBaseDetailWithMutex(Long productId) {
        String cacheKey = ProductCacheConstants.baseDetailKey(productId);
        String lockKey = ProductCacheConstants.baseDetailLockKey(productId);
        String token = tryAcquireCacheRebuildLock(lockKey);
        if (token != null) {
            try {
                String rechecked = stringRedisTemplate.opsForValue().get(cacheKey);
                if (rechecked != null) {
                    return parseCachedValue(cacheKey, rechecked, productCacheCodec::parseBaseDetail);
                }
                ProductBaseCacheItem loaded = loadActiveProductBaseFromDb(productId);
                if (loaded == null) {
                    safeSet(cacheKey, ProductCacheConstants.NULL_MARKER, nullCacheTtl());
                } else {
                    safeSet(cacheKey, productCacheCodec.toJson(loaded), baseCacheTtl());
                }
                return loaded;
            } finally {
                distributedLockService.release(lockKey, token);
            }
        }

        ProductBaseCacheItem waited = waitForCacheValue(
                cacheKey,
                cached -> parseCachedValue(cacheKey, cached, productCacheCodec::parseBaseDetail));
        return waited != null ? waited : loadActiveProductBaseFromDb(productId);
    }

    /** 使用互斥锁重建单商品统计缓存。 */
    private ProductStatsCacheItem rebuildStatsWithMutex(Long productId) {
        String cacheKey = ProductCacheConstants.statsKey(productId);
        String lockKey = ProductCacheConstants.statsLockKey(productId);
        String token = tryAcquireCacheRebuildLock(lockKey);
        if (token != null) {
            try {
                String rechecked = stringRedisTemplate.opsForValue().get(cacheKey);
                if (rechecked != null) {
                    return parseCachedValue(cacheKey, rechecked, productCacheCodec::parseStats);
                }
                ProductStatsCacheItem loaded = loadProductStatsFromDb(productId);
                if (loaded == null) {
                    safeSet(cacheKey, ProductCacheConstants.NULL_MARKER, nullCacheTtl());
                } else {
                    safeSet(cacheKey, productCacheCodec.toJson(loaded), statsCacheTtl());
                }
                return loaded;
            } finally {
                distributedLockService.release(lockKey, token);
            }
        }

        ProductStatsCacheItem waited = waitForCacheValue(
                cacheKey,
                cached -> parseCachedValue(cacheKey, cached, productCacheCodec::parseStats));
        return waited != null ? waited : loadProductStatsFromDb(productId);
    }

    /** 从数据库加载所有活动商品的基础信息。 */
    private List<ProductBaseCacheItem> loadActiveProductBasesFromDb() {
        return productMapper.findActiveProducts().stream().map(this::toBaseItem).toList();
    }

    /** 从数据库加载单商品基础信息，下架商品视为不存在。 */
    private ProductBaseCacheItem loadActiveProductBaseFromDb(Long productId) {
        ShopProduct product = productMapper.findById(productId);
        if (product == null || !ProductStatus.ACTIVE.equals(product.getStatus())) {
            return null;
        }
        return toBaseItem(product);
    }

    /** 从数据库读取单商品实时库存和销量。 */
    private ProductStatsCacheItem loadProductStatsFromDb(Long productId) {
        return productMapper.findStatsByIds(List.of(productId)).stream().findFirst().orElse(null);
    }

    /** 从数据库批量读取商品实时库存和销量。 */
    private Map<Long, ProductStatsCacheItem> loadStatsMapFromDb(List<Long> productIds) {
        Map<Long, ProductStatsCacheItem> result = new LinkedHashMap<>();
        for (ProductStatsCacheItem item : productMapper.findStatsByIds(productIds)) {
            result.put(item.productId(), item);
        }
        return result;
    }

    /** 将商品实体映射为基础缓存对象。 */
    private ProductBaseCacheItem toBaseItem(ShopProduct product) {
        return new ProductBaseCacheItem(
                product.getId(),
                product.getSku(),
                product.getTitle(),
                product.getVendor(),
                product.getPlanName(),
                product.getDescription(),
                product.getPrice(),
                product.getStatus(),
                product.getSortOrder());
    }

    /** 统一处理缓存反序列化失败时的 key 驱逐。 */
    private <T> T parseCachedValue(String cacheKey, String cached, Function<String, T> parser) {
        try {
            return parser.apply(cached);
        } catch (Exception exception) {
            safeDelete(cacheKey);
            throw exception;
        }
    }

    /** 在热点 key 重建期间，短暂等待其他线程把缓存写回。 */
    private <T> T waitForCacheValue(String cacheKey, Function<String, T> parser) {
        for (int attempt = 0; attempt < 3; attempt++) {
            sleepBriefly();
            try {
                String cached = stringRedisTemplate.opsForValue().get(cacheKey);
                if (cached != null) {
                    return parser.apply(cached);
                }
            } catch (Exception exception) {
                return null;
            }
        }
        return null;
    }

    /** 尝试获取缓存重建锁，Redis 异常时降级为直接回源数据库。 */
    private String tryAcquireCacheRebuildLock(String lockKey) {
        try {
            return distributedLockService.tryAcquire(
                    lockKey,
                    ProductCacheConstants.CACHE_REBUILD_WAIT_TIMEOUT,
                    ProductCacheConstants.CACHE_REBUILD_LEASE_DURATION);
        } catch (Exception exception) {
            log.warn("获取商品缓存重建锁失败，lockKey={}", lockKey, exception);
            return null;
        }
    }

    /** 安全写入缓存，异常只记录日志，不影响主业务结果。 */
    private void safeSet(String key, String value, Duration ttl) {
        try {
            stringRedisTemplate.opsForValue().set(key, value, ttl);
        } catch (Exception exception) {
            log.warn("写入商品缓存失败，key={}", key, exception);
        }
    }

    /** 安全删除缓存，异常只记录日志，不影响主业务结果。 */
    private void safeDelete(String... keys) {
        if (keys == null || keys.length == 0) {
            return;
        }
        try {
            stringRedisTemplate.delete(List.of(keys));
        } catch (Exception exception) {
            log.warn("删除商品缓存失败，keys={}", List.of(keys), exception);
        }
    }

    /** 生成基础信息缓存的随机 TTL。 */
    private Duration baseCacheTtl() {
        return CacheTtlUtils.withJitter(ProductCacheConstants.BASE_CACHE_TTL, ProductCacheConstants.BASE_CACHE_JITTER);
    }

    /** 生成统计缓存的随机 TTL。 */
    private Duration statsCacheTtl() {
        return CacheTtlUtils.withJitter(ProductCacheConstants.STATS_CACHE_TTL, ProductCacheConstants.STATS_CACHE_JITTER);
    }

    /** 生成空值缓存的随机 TTL。 */
    private Duration nullCacheTtl() {
        return CacheTtlUtils.withJitter(ProductCacheConstants.NULL_CACHE_TTL, ProductCacheConstants.NULL_CACHE_JITTER);
    }

    /** 热点 key 等待重建时的短暂休眠。 */
    private void sleepBriefly() {
        try {
            Thread.sleep(60L);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }
}
