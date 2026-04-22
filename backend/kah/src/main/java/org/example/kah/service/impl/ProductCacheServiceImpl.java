package org.example.kah.service.impl;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import org.example.kah.cache.ProductBaseCacheItem;
import org.example.kah.cache.ProductCacheCodec;
import org.example.kah.cache.ProductCacheConstants;
import org.example.kah.cache.ProductStatsCacheItem;
import org.example.kah.entity.ProductStatus;
import org.example.kah.entity.ShopProduct;
import org.example.kah.mapper.ProductMapper;
import org.example.kah.metrics.ShopMetricsService;
import org.example.kah.service.DistributedLockService;
import org.example.kah.service.ProductCacheService;
import org.example.kah.util.CacheTtlUtils;
import org.example.kah.util.LongIdUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ProductCacheServiceImpl implements ProductCacheService {

    private static final Logger log = LoggerFactory.getLogger(ProductCacheServiceImpl.class);
    private static final int KEY_SCAN_BATCH_SIZE = 200;
    private static final String ACTIVE_BASE_LIST_LOCAL_KEY = "active-base-list";

    private final StringRedisTemplate stringRedisTemplate;
    private final ProductCacheCodec productCacheCodec;
    private final ProductMapper productMapper;
    private final DistributedLockService distributedLockService;
    private final ShopMetricsService shopMetricsService;
    private final Cache<String, List<ProductBaseCacheItem>> activeProductBaseListLocalCache = Caffeine.newBuilder()
            .maximumSize(1)
            .expireAfterWrite(ProductCacheConstants.BASE_LOCAL_CACHE_TTL)
            .build();
    private final Cache<Long, ProductBaseCacheItem> activeProductBaseLocalCache = Caffeine.newBuilder()
            .maximumSize(ProductCacheConstants.BASE_LOCAL_CACHE_MAXIMUM_SIZE)
            .expireAfterWrite(ProductCacheConstants.BASE_LOCAL_CACHE_TTL)
            .build();
    private final Cache<Long, ProductStatsCacheItem> productStatsLocalCache = Caffeine.newBuilder()
            .maximumSize(ProductCacheConstants.STATS_LOCAL_CACHE_MAXIMUM_SIZE)
            .expireAfterWrite(ProductCacheConstants.STATS_LOCAL_CACHE_TTL)
            .build();

    @Override
    public List<ProductBaseCacheItem> getActiveProductBases() {
        List<ProductBaseCacheItem> localCached = activeProductBaseListLocalCache.getIfPresent(ACTIVE_BASE_LIST_LOCAL_KEY);
        if (localCached != null) {
            shopMetricsService.recordProductBaseCacheHit();
            return localCached;
        }

        try {
            String cached = stringRedisTemplate.opsForValue().get(ProductCacheConstants.ACTIVE_PRODUCT_BASE_LIST_KEY);
            if (cached != null) {
                shopMetricsService.recordProductBaseCacheHit();
                List<ProductBaseCacheItem> parsed = parseCachedValue(
                        ProductCacheConstants.ACTIVE_PRODUCT_BASE_LIST_KEY,
                        cached,
                        productCacheCodec::parseBaseList);
                cacheActiveBaseListLocally(parsed);
                return parsed;
            }
            shopMetricsService.recordProductBaseCacheMiss();
            return rebuildActiveBaseListWithMutex();
        } catch (Exception exception) {
            shopMetricsService.recordProductBaseCacheFallback();
            log.warn("Failed to read active product base list from cache, falling back to database", exception);
            List<ProductBaseCacheItem> loaded = loadActiveProductBasesFromDb();
            cacheActiveBaseListLocally(loaded);
            return loaded;
        }
    }

    @Override
    public ProductBaseCacheItem getActiveProductBase(Long productId) {
        if (!LongIdUtils.isPositive(productId)) {
            return null;
        }

        ProductBaseCacheItem localCached = activeProductBaseLocalCache.getIfPresent(productId);
        if (localCached != null) {
            shopMetricsService.recordProductBaseCacheHit();
            return localCached;
        }

        try {
            String cached = stringRedisTemplate.opsForValue().get(ProductCacheConstants.baseDetailKey(productId));
            if (cached != null) {
                shopMetricsService.recordProductBaseCacheHit();
                ProductBaseCacheItem parsed = parseCachedValue(
                        ProductCacheConstants.baseDetailKey(productId),
                        cached,
                        productCacheCodec::parseBaseDetail);
                if (parsed != null) {
                    activeProductBaseLocalCache.put(productId, parsed);
                }
                return parsed;
            }
            shopMetricsService.recordProductBaseCacheMiss();
            return rebuildBaseDetailWithMutex(productId);
        } catch (Exception exception) {
            shopMetricsService.recordProductBaseCacheFallback();
            log.warn("Failed to read product base from cache, falling back to database, productId={}", productId, exception);
            ProductBaseCacheItem loaded = loadActiveProductBaseFromDb(productId);
            if (loaded != null) {
                activeProductBaseLocalCache.put(productId, loaded);
            }
            return loaded;
        }
    }

    @Override
    public Map<Long, ProductStatsCacheItem> getProductStats(List<Long> productIds) {
        List<Long> normalizedIds = LongIdUtils.normalizeDistinctPositiveIds(productIds);
        if (normalizedIds.isEmpty()) {
            return Map.of();
        }

        Map<Long, ProductStatsCacheItem> result = new LinkedHashMap<>();
        List<Long> redisLookupIds = new ArrayList<>();
        int localHitCount = 0;

        for (Long productId : normalizedIds) {
            ProductStatsCacheItem localCached = productStatsLocalCache.getIfPresent(productId);
            if (localCached != null) {
                result.put(productId, localCached);
                localHitCount++;
            } else {
                redisLookupIds.add(productId);
            }
        }

        if (localHitCount > 0) {
            shopMetricsService.recordProductStatsCacheHit(localHitCount);
        }
        if (redisLookupIds.isEmpty()) {
            return result;
        }

        try {
            List<String> keys = redisLookupIds.stream().map(ProductCacheConstants::statsKey).toList();
            List<String> cachedValues = stringRedisTemplate.opsForValue().multiGet(keys);
            List<Long> missingIds = new ArrayList<>();
            int hitCount = 0;

            for (int index = 0; index < redisLookupIds.size(); index++) {
                Long productId = redisLookupIds.get(index);
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
                    productStatsLocalCache.put(productId, item);
                    hitCount++;
                }
            }

            if (hitCount > 0) {
                shopMetricsService.recordProductStatsCacheHit(hitCount);
            }
            if (missingIds.size() == 1) {
                Long missingId = missingIds.get(0);
                ProductStatsCacheItem item = getProductStats(missingId);
                if (item != null) {
                    result.put(missingId, item);
                }
                return result;
            }
            if (!missingIds.isEmpty()) {
                shopMetricsService.recordProductStatsCacheMiss(missingIds.size());
                result.putAll(rebuildMissingStatsBatch(missingIds));
            }
            return result;
        } catch (Exception exception) {
            shopMetricsService.recordProductStatsCacheFallback(normalizedIds.size());
            log.warn("Failed to read product stats in batch from cache, falling back to database", exception);
            Map<Long, ProductStatsCacheItem> loaded = loadStatsMapFromDb(normalizedIds);
            loaded.forEach(productStatsLocalCache::put);
            return loaded;
        }
    }

    @Override
    public ProductStatsCacheItem getProductStats(Long productId) {
        if (!LongIdUtils.isPositive(productId)) {
            return null;
        }

        ProductStatsCacheItem localCached = productStatsLocalCache.getIfPresent(productId);
        if (localCached != null) {
            shopMetricsService.recordProductStatsCacheHit(1);
            return localCached;
        }

        try {
            String key = ProductCacheConstants.statsKey(productId);
            String cached = stringRedisTemplate.opsForValue().get(key);
            if (cached != null) {
                shopMetricsService.recordProductStatsCacheHit(1);
                ProductStatsCacheItem parsed = parseCachedValue(key, cached, productCacheCodec::parseStats);
                if (parsed != null) {
                    productStatsLocalCache.put(productId, parsed);
                }
                return parsed;
            }
            shopMetricsService.recordProductStatsCacheMiss(1);
            return rebuildStatsWithMutex(productId);
        } catch (Exception exception) {
            shopMetricsService.recordProductStatsCacheFallback(1);
            log.warn("Failed to read product stats from cache, falling back to database, productId={}", productId, exception);
            ProductStatsCacheItem loaded = loadProductStatsFromDb(productId);
            if (loaded != null) {
                productStatsLocalCache.put(productId, loaded);
            }
            return loaded;
        }
    }

    @Override
    public void refreshProductBase(Long productId) {
        if (!LongIdUtils.isPositive(productId)) {
            return;
        }

        ShopProduct product = productMapper.findById(productId);
        if (product == null || !ProductStatus.ACTIVE.equals(product.getStatus())) {
            safeSet(ProductCacheConstants.baseDetailKey(productId), ProductCacheConstants.NULL_MARKER, nullCacheTtl());
            activeProductBaseLocalCache.invalidate(productId);
            activeProductBaseListLocalCache.invalidate(ACTIVE_BASE_LIST_LOCAL_KEY);
            evictProductStats(productId);
            safeDelete(ProductCacheConstants.ACTIVE_PRODUCT_BASE_LIST_KEY);
            return;
        }

        ProductBaseCacheItem baseItem = toBaseItem(product);
        safeSetPersistent(ProductCacheConstants.baseDetailKey(productId), productCacheCodec.toJson(baseItem));
        activeProductBaseLocalCache.put(productId, baseItem);
        activeProductBaseListLocalCache.invalidate(ACTIVE_BASE_LIST_LOCAL_KEY);
        safeDelete(ProductCacheConstants.ACTIVE_PRODUCT_BASE_LIST_KEY);
    }

    @Override
    public void refreshProductStats(Long productId) {
        if (!LongIdUtils.isPositive(productId)) {
            return;
        }

        ProductStatsCacheItem item = loadProductStatsFromDb(productId);
        if (item == null) {
            safeSet(ProductCacheConstants.statsKey(productId), ProductCacheConstants.NULL_MARKER, nullCacheTtl());
            productStatsLocalCache.invalidate(productId);
            return;
        }

        safeSet(ProductCacheConstants.statsKey(productId), productCacheCodec.toJson(item), statsCacheTtl());
        productStatsLocalCache.put(productId, item);
    }

    @Override
    public void evictProductBase(Long productId) {
        if (!LongIdUtils.isPositive(productId)) {
            return;
        }

        activeProductBaseLocalCache.invalidate(productId);
        activeProductBaseListLocalCache.invalidate(ACTIVE_BASE_LIST_LOCAL_KEY);
        safeDelete(ProductCacheConstants.baseDetailKey(productId), ProductCacheConstants.ACTIVE_PRODUCT_BASE_LIST_KEY);
    }

    @Override
    public void evictProductStats(Long productId) {
        if (!LongIdUtils.isPositive(productId)) {
            return;
        }

        productStatsLocalCache.invalidate(productId);
        safeDelete(ProductCacheConstants.statsKey(productId));
    }

    @Override
    public void removeProduct(Long productId) {
        if (!LongIdUtils.isPositive(productId)) {
            return;
        }

        safeSet(ProductCacheConstants.baseDetailKey(productId), ProductCacheConstants.NULL_MARKER, nullCacheTtl());
        activeProductBaseLocalCache.invalidate(productId);
        activeProductBaseListLocalCache.invalidate(ACTIVE_BASE_LIST_LOCAL_KEY);
        productStatsLocalCache.invalidate(productId);
        safeDelete(ProductCacheConstants.statsKey(productId), ProductCacheConstants.ACTIVE_PRODUCT_BASE_LIST_KEY);
    }

    @Override
    public void clearAllProductCaches() {
        try {
            deleteKeysByPattern(ProductCacheConstants.PRODUCT_CACHE_KEY_PREFIX + "*");
            activeProductBaseListLocalCache.invalidateAll();
            activeProductBaseLocalCache.invalidateAll();
            productStatsLocalCache.invalidateAll();
        } catch (Exception exception) {
            log.warn("Failed to clear all product caches", exception);
        }
    }

    @Override
    public void warmupActiveProductBases() {
        List<ProductBaseCacheItem> loaded = loadActiveProductBasesFromDb();
        safeSetPersistent(ProductCacheConstants.ACTIVE_PRODUCT_BASE_LIST_KEY, productCacheCodec.toJson(loaded));
        cacheActiveBaseListLocally(loaded);
    }

    private List<ProductBaseCacheItem> rebuildActiveBaseListWithMutex() {
        String token = tryAcquireCacheRebuildLock(ProductCacheConstants.ACTIVE_PRODUCT_BASE_LIST_LOCK_KEY);
        if (token != null) {
            try {
                String rechecked = stringRedisTemplate.opsForValue().get(ProductCacheConstants.ACTIVE_PRODUCT_BASE_LIST_KEY);
                if (rechecked != null) {
                    List<ProductBaseCacheItem> parsed = parseCachedValue(
                            ProductCacheConstants.ACTIVE_PRODUCT_BASE_LIST_KEY,
                            rechecked,
                            productCacheCodec::parseBaseList);
                    cacheActiveBaseListLocally(parsed);
                    return parsed;
                }

                List<ProductBaseCacheItem> loaded = loadActiveProductBasesFromDb();
                safeSetPersistent(ProductCacheConstants.ACTIVE_PRODUCT_BASE_LIST_KEY, productCacheCodec.toJson(loaded));
                cacheActiveBaseListLocally(loaded);
                shopMetricsService.recordProductBaseCacheRebuild();
                return loaded;
            } finally {
                distributedLockService.release(ProductCacheConstants.ACTIVE_PRODUCT_BASE_LIST_LOCK_KEY, token);
            }
        }

        List<ProductBaseCacheItem> waited = waitForCacheValue(
                ProductCacheConstants.ACTIVE_PRODUCT_BASE_LIST_KEY,
                cached -> parseCachedValue(ProductCacheConstants.ACTIVE_PRODUCT_BASE_LIST_KEY, cached, productCacheCodec::parseBaseList));
        if (waited != null) {
            cacheActiveBaseListLocally(waited);
            return waited;
        }

        List<ProductBaseCacheItem> loaded = loadActiveProductBasesFromDb();
        cacheActiveBaseListLocally(loaded);
        return loaded;
    }

    private ProductBaseCacheItem rebuildBaseDetailWithMutex(Long productId) {
        String cacheKey = ProductCacheConstants.baseDetailKey(productId);
        String lockKey = ProductCacheConstants.baseDetailLockKey(productId);
        String token = tryAcquireCacheRebuildLock(lockKey);
        if (token != null) {
            try {
                String rechecked = stringRedisTemplate.opsForValue().get(cacheKey);
                if (rechecked != null) {
                    ProductBaseCacheItem parsed = parseCachedValue(cacheKey, rechecked, productCacheCodec::parseBaseDetail);
                    if (parsed != null) {
                        activeProductBaseLocalCache.put(productId, parsed);
                    }
                    return parsed;
                }

                ProductBaseCacheItem loaded = loadActiveProductBaseFromDb(productId);
                if (loaded == null) {
                    safeSet(cacheKey, ProductCacheConstants.NULL_MARKER, nullCacheTtl());
                } else {
                    safeSetPersistent(cacheKey, productCacheCodec.toJson(loaded));
                    activeProductBaseLocalCache.put(productId, loaded);
                }
                shopMetricsService.recordProductBaseCacheRebuild();
                return loaded;
            } finally {
                distributedLockService.release(lockKey, token);
            }
        }

        ProductBaseCacheItem waited = waitForCacheValue(
                cacheKey,
                cached -> parseCachedValue(cacheKey, cached, productCacheCodec::parseBaseDetail));
        if (waited != null) {
            activeProductBaseLocalCache.put(productId, waited);
            return waited;
        }

        ProductBaseCacheItem loaded = loadActiveProductBaseFromDb(productId);
        if (loaded != null) {
            activeProductBaseLocalCache.put(productId, loaded);
        }
        return loaded;
    }

    private ProductStatsCacheItem rebuildStatsWithMutex(Long productId) {
        String cacheKey = ProductCacheConstants.statsKey(productId);
        String lockKey = ProductCacheConstants.statsLockKey(productId);
        String token = tryAcquireCacheRebuildLock(lockKey);
        if (token != null) {
            try {
                String rechecked = stringRedisTemplate.opsForValue().get(cacheKey);
                if (rechecked != null) {
                    ProductStatsCacheItem parsed = parseCachedValue(cacheKey, rechecked, productCacheCodec::parseStats);
                    if (parsed != null) {
                        productStatsLocalCache.put(productId, parsed);
                    }
                    return parsed;
                }

                ProductStatsCacheItem loaded = loadProductStatsFromDb(productId);
                if (loaded == null) {
                    safeSet(cacheKey, ProductCacheConstants.NULL_MARKER, nullCacheTtl());
                } else {
                    safeSet(cacheKey, productCacheCodec.toJson(loaded), statsCacheTtl());
                    productStatsLocalCache.put(productId, loaded);
                }
                shopMetricsService.recordProductStatsCacheRebuild();
                return loaded;
            } finally {
                distributedLockService.release(lockKey, token);
            }
        }

        ProductStatsCacheItem waited = waitForCacheValue(
                cacheKey,
                cached -> parseCachedValue(cacheKey, cached, productCacheCodec::parseStats));
        if (waited != null) {
            productStatsLocalCache.put(productId, waited);
            return waited;
        }

        ProductStatsCacheItem loaded = loadProductStatsFromDb(productId);
        if (loaded != null) {
            productStatsLocalCache.put(productId, loaded);
        }
        return loaded;
    }

    private Map<Long, ProductStatsCacheItem> rebuildMissingStatsBatch(List<Long> productIds) {
        Map<Long, ProductStatsCacheItem> loadedStats = loadStatsMapFromDb(productIds);
        Map<Long, ProductStatsCacheItem> result = new LinkedHashMap<>();
        for (Long productId : productIds) {
            ProductStatsCacheItem item = loadedStats.get(productId);
            String cacheKey = ProductCacheConstants.statsKey(productId);
            if (item == null) {
                safeSet(cacheKey, ProductCacheConstants.NULL_MARKER, nullCacheTtl());
                continue;
            }
            safeSet(cacheKey, productCacheCodec.toJson(item), statsCacheTtl());
            productStatsLocalCache.put(productId, item);
            result.put(productId, item);
        }
        shopMetricsService.recordProductStatsCacheRebuild(productIds.size());
        return result;
    }

    private List<ProductBaseCacheItem> loadActiveProductBasesFromDb() {
        return productMapper.findActiveProducts().stream().map(this::toBaseItem).toList();
    }

    private ProductBaseCacheItem loadActiveProductBaseFromDb(Long productId) {
        ShopProduct product = productMapper.findById(productId);
        if (product == null || !ProductStatus.ACTIVE.equals(product.getStatus())) {
            return null;
        }
        return toBaseItem(product);
    }

    private ProductStatsCacheItem loadProductStatsFromDb(Long productId) {
        return productMapper.findStatsByIds(List.of(productId)).stream().findFirst().orElse(null);
    }

    private Map<Long, ProductStatsCacheItem> loadStatsMapFromDb(List<Long> productIds) {
        Map<Long, ProductStatsCacheItem> result = new LinkedHashMap<>();
        for (ProductStatsCacheItem item : productMapper.findStatsByIds(productIds)) {
            result.put(item.productId(), item);
        }
        return result;
    }

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

    private <T> T parseCachedValue(String cacheKey, String cached, Function<String, T> parser) {
        try {
            return parser.apply(cached);
        } catch (Exception exception) {
            safeDelete(cacheKey);
            throw exception;
        }
    }

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

    private String tryAcquireCacheRebuildLock(String lockKey) {
        try {
            return distributedLockService.tryAcquire(
                    lockKey,
                    ProductCacheConstants.CACHE_REBUILD_WAIT_TIMEOUT,
                    ProductCacheConstants.CACHE_REBUILD_LEASE_DURATION);
        } catch (Exception exception) {
            log.warn("Failed to acquire product cache rebuild lock, lockKey={}", lockKey, exception);
            return null;
        }
    }

    private void safeSet(String key, String value, Duration ttl) {
        try {
            stringRedisTemplate.opsForValue().set(key, value, ttl);
        } catch (Exception exception) {
            log.warn("Failed to write product cache entry, key={}", key, exception);
        }
    }

    private void safeSetPersistent(String key, String value) {
        try {
            stringRedisTemplate.opsForValue().set(key, value);
        } catch (Exception exception) {
            log.warn("Failed to write persistent product cache entry, key={}", key, exception);
        }
    }

    private void safeDelete(String... keys) {
        if (keys == null || keys.length == 0) {
            return;
        }
        try {
            stringRedisTemplate.delete(List.of(keys));
        } catch (Exception exception) {
            log.warn("Failed to delete product cache entries, keys={}", List.of(keys), exception);
        }
    }

    private void deleteKeysByPattern(String pattern) {
        stringRedisTemplate.execute((RedisCallback<Void>) connection -> {
            ScanOptions scanOptions = ScanOptions.scanOptions().match(pattern).count(KEY_SCAN_BATCH_SIZE).build();
            try (Cursor<byte[]> cursor = connection.scan(scanOptions)) {
                List<byte[]> batch = new ArrayList<>();
                while (cursor.hasNext()) {
                    batch.add(cursor.next());
                    if (batch.size() >= KEY_SCAN_BATCH_SIZE) {
                        deleteRawKeys(connection, batch);
                    }
                }
                deleteRawKeys(connection, batch);
            } catch (Exception exception) {
                throw new IllegalStateException("Failed to scan and delete Redis keys", exception);
            }
            return null;
        });
    }

    private void deleteRawKeys(RedisConnection connection, List<byte[]> batch) {
        if (batch.isEmpty()) {
            return;
        }
        connection.del(batch.toArray(byte[][]::new));
        batch.clear();
    }

    private Duration statsCacheTtl() {
        return CacheTtlUtils.withJitter(ProductCacheConstants.STATS_CACHE_TTL, ProductCacheConstants.STATS_CACHE_JITTER);
    }

    private Duration nullCacheTtl() {
        return CacheTtlUtils.withJitter(ProductCacheConstants.NULL_CACHE_TTL, ProductCacheConstants.NULL_CACHE_JITTER);
    }

    private void cacheActiveBaseListLocally(List<ProductBaseCacheItem> items) {
        activeProductBaseListLocalCache.put(ACTIVE_BASE_LIST_LOCAL_KEY, items);
        for (ProductBaseCacheItem item : items) {
            activeProductBaseLocalCache.put(item.id(), item);
        }
    }

    private void sleepBriefly() {
        try {
            Thread.sleep(60L);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }
}
