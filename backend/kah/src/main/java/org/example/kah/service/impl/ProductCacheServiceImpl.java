package org.example.kah.service.impl;

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
import org.example.kah.service.DistributedLockService;
import org.example.kah.service.ProductCacheService;
import org.example.kah.util.CacheTtlUtils;
import org.example.kah.util.LongIdUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ProductCacheServiceImpl implements ProductCacheService {

    private static final Logger log = LoggerFactory.getLogger(ProductCacheServiceImpl.class);
    private static final int KEY_SCAN_BATCH_SIZE = 200;

    private final StringRedisTemplate stringRedisTemplate;
    private final ProductCacheCodec productCacheCodec;
    private final ProductMapper productMapper;
    private final DistributedLockService distributedLockService;

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

    @Override
    public void evictProductBase(Long productId) {
        if (!LongIdUtils.isPositive(productId)) {
            return;
        }
        safeDelete(ProductCacheConstants.baseDetailKey(productId), ProductCacheConstants.ACTIVE_PRODUCT_BASE_LIST_KEY);
    }

    @Override
    public void evictProductStats(Long productId) {
        if (!LongIdUtils.isPositive(productId)) {
            return;
        }
        safeDelete(ProductCacheConstants.statsKey(productId));
    }

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

    @Override
    public void clearAllProductCaches() {
        try {
            deleteKeysByPattern(ProductCacheConstants.PRODUCT_CACHE_KEY_PREFIX + "*");
        } catch (Exception exception) {
            log.warn("清理全部商品缓存失败", exception);
        }
    }

    @Override
    public void warmupActiveProductBases() {
        safeSet(
                ProductCacheConstants.ACTIVE_PRODUCT_BASE_LIST_KEY,
                productCacheCodec.toJson(loadActiveProductBasesFromDb()),
                baseCacheTtl());
    }

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
            log.warn("获取商品缓存重建锁失败，lockKey={}", lockKey, exception);
            return null;
        }
    }

    private void safeSet(String key, String value, Duration ttl) {
        try {
            stringRedisTemplate.opsForValue().set(key, value, ttl);
        } catch (Exception exception) {
            log.warn("写入商品缓存失败，key={}", key, exception);
        }
    }

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
                throw new IllegalStateException("扫描并删除 Redis 键失败", exception);
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

    private Duration baseCacheTtl() {
        return CacheTtlUtils.withJitter(ProductCacheConstants.BASE_CACHE_TTL, ProductCacheConstants.BASE_CACHE_JITTER);
    }

    private Duration statsCacheTtl() {
        return CacheTtlUtils.withJitter(ProductCacheConstants.STATS_CACHE_TTL, ProductCacheConstants.STATS_CACHE_JITTER);
    }

    private Duration nullCacheTtl() {
        return CacheTtlUtils.withJitter(ProductCacheConstants.NULL_CACHE_TTL, ProductCacheConstants.NULL_CACHE_JITTER);
    }

    private void sleepBriefly() {
        try {
            Thread.sleep(60L);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }
}