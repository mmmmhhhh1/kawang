package org.example.kah.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import lombok.RequiredArgsConstructor;
import org.example.kah.cache.ProductBaseCacheItem;
import org.example.kah.cache.ProductStatsCacheItem;
import org.example.kah.entity.ProductStatus;
import org.example.kah.entity.ShopProduct;
import org.example.kah.mapper.ProductMapper;
import org.example.kah.service.DistributedLockService;
import org.example.kah.service.ProductCacheService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * {@link ProductCacheService} 閻ㄥ嫰绮拋銈呯杽閻滆埇鈧? * 娴ｈ法鏁ら垾婊冪唨绾偓娣団剝浼?+ 缂佺喕顓告穱鈩冧紖閳ユ繂寮荤仦鍌滅处鐎涙﹢妾锋担搴″閸欐媽顕版惔鎾活暥閻滃浄绱濋獮璺侯嚠缁屽潡鈧繈鈧礁鍤粚鍨嫲闂嗩亜绌块崑姘唨绾偓闂冨弶濮㈤妴? */
@Service
@RequiredArgsConstructor
public class ProductCacheServiceImpl implements ProductCacheService {

    private static final Logger log = LoggerFactory.getLogger(ProductCacheServiceImpl.class);
    private static final TypeReference<List<ProductBaseCacheItem>> BASE_LIST_TYPE = new TypeReference<>() {
    };

    private static final String PRODUCT_CACHE_KEY_PREFIX = "product:";
    private static final String ACTIVE_PRODUCT_BASE_LIST_KEY = PRODUCT_CACHE_KEY_PREFIX + "list:active:base";
    private static final String PRODUCT_BASE_DETAIL_KEY_PREFIX = PRODUCT_CACHE_KEY_PREFIX + "detail:";
    private static final String PRODUCT_STATS_KEY_PREFIX = PRODUCT_CACHE_KEY_PREFIX + "stats:";
    private static final String NULL_MARKER = "__NULL__";

    private static final String ACTIVE_PRODUCT_BASE_LIST_LOCK_KEY = "lock:cache:product:list:active:base";
    private static final String PRODUCT_BASE_DETAIL_LOCK_KEY_PREFIX = "lock:cache:product:detail:";
    private static final String PRODUCT_STATS_LOCK_KEY_PREFIX = "lock:cache:product:stats:";

    private static final Duration BASE_CACHE_TTL = Duration.ofMinutes(30);
    private static final Duration BASE_CACHE_JITTER = Duration.ofMinutes(5);
    private static final Duration STATS_CACHE_TTL = Duration.ofSeconds(60);
    private static final Duration STATS_CACHE_JITTER = Duration.ofSeconds(15);
    private static final Duration NULL_CACHE_TTL = Duration.ofMinutes(2);
    private static final Duration NULL_CACHE_JITTER = Duration.ofSeconds(30);
    private static final Duration CACHE_REBUILD_WAIT_TIMEOUT = Duration.ofMillis(250);
    private static final Duration CACHE_REBUILD_LEASE_DURATION = Duration.ofSeconds(5);

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    private final ProductMapper productMapper;
    private final DistributedLockService distributedLockService;

    @Override
    public List<ProductBaseCacheItem> getActiveProductBases() {
        try {
            String cached = stringRedisTemplate.opsForValue().get(ACTIVE_PRODUCT_BASE_LIST_KEY);
            if (cached != null) {
                return parseBaseList(cached);
            }
            return rebuildActiveBaseListWithMutex();
        } catch (Exception exception) {
            log.warn("Clear product cache failed", exception);
            return loadActiveProductBasesFromDb();
        }
    }

    @Override
    public ProductBaseCacheItem getActiveProductBase(Long productId) {
        if (!isValidProductId(productId)) {
            return null;
        }
        try {
            String cached = stringRedisTemplate.opsForValue().get(baseDetailKey(productId));
            if (cached != null) {
                return parseBaseDetail(cached);
            }
            return rebuildBaseDetailWithMutex(productId);
        } catch (Exception exception) {
            log.warn("Clear product cache failed", exception);
            return loadActiveProductBaseFromDb(productId);
        }
    }

    @Override
    public Map<Long, ProductStatsCacheItem> getProductStats(List<Long> productIds) {
        List<Long> normalizedIds = normalizeIds(productIds);
        if (normalizedIds.isEmpty()) {
            return Map.of();
        }

        try {
            List<String> keys = normalizedIds.stream().map(this::statsKey).toList();
            List<String> cachedValues = stringRedisTemplate.opsForValue().multiGet(keys);
            Map<Long, ProductStatsCacheItem> result = new LinkedHashMap<>();
            List<Long> missingIds = new ArrayList<>();
            for (int index = 0; index < normalizedIds.size(); index++) {
                Long productId = normalizedIds.get(index);
                String cached = cachedValues == null ? null : cachedValues.get(index);
                if (cached == null) {
                    missingIds.add(productId);
                    continue;
                }
                ProductStatsCacheItem item = parseStats(cached);
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
            log.warn("Clear product cache failed", exception);
            return loadStatsMapFromDb(normalizedIds);
        }
    }

    @Override
    public ProductStatsCacheItem getProductStats(Long productId) {
        if (!isValidProductId(productId)) {
            return null;
        }
        try {
            String cached = stringRedisTemplate.opsForValue().get(statsKey(productId));
            if (cached != null) {
                return parseStats(cached);
            }
            return rebuildStatsWithMutex(productId);
        } catch (Exception exception) {
            log.warn("Clear product cache failed", exception);
            return loadProductStatsFromDb(productId);
        }
    }

    @Override
    public void refreshProductBase(Long productId) {
        if (!isValidProductId(productId)) {
            return;
        }
        ShopProduct product = productMapper.findById(productId);
        if (product == null || !ProductStatus.ACTIVE.equals(product.getStatus())) {
            safeSet(baseDetailKey(productId), NULL_MARKER, nullCacheTtl());
            evictProductStats(productId);
        } else {
            safeSet(baseDetailKey(productId), toJson(toBaseItem(product)), baseCacheTtl());
        }
        safeSet(ACTIVE_PRODUCT_BASE_LIST_KEY, toJson(loadActiveProductBasesFromDb()), baseCacheTtl());
    }

    @Override
    public void refreshProductStats(Long productId) {
        if (!isValidProductId(productId)) {
            return;
        }
        ProductStatsCacheItem item = loadProductStatsFromDb(productId);
        if (item == null) {
            safeSet(statsKey(productId), NULL_MARKER, nullCacheTtl());
            return;
        }
        safeSet(statsKey(productId), toJson(item), statsCacheTtl());
    }

    @Override
    public void evictProductBase(Long productId) {
        if (!isValidProductId(productId)) {
            return;
        }
        safeDelete(baseDetailKey(productId), ACTIVE_PRODUCT_BASE_LIST_KEY);
    }

    @Override
    public void evictProductStats(Long productId) {
        if (!isValidProductId(productId)) {
            return;
        }
        safeDelete(statsKey(productId));
    }

    @Override
    public void removeProduct(Long productId) {
        if (!isValidProductId(productId)) {
            return;
        }
        safeSet(baseDetailKey(productId), NULL_MARKER, nullCacheTtl());
        safeDelete(statsKey(productId));
        safeSet(ACTIVE_PRODUCT_BASE_LIST_KEY, toJson(loadActiveProductBasesFromDb()), baseCacheTtl());
    }

    @Override
    public void clearAllProductCaches() {
        try {
            Set<String> keys = stringRedisTemplate.keys(PRODUCT_CACHE_KEY_PREFIX + "*");
            if (keys != null && !keys.isEmpty()) {
                stringRedisTemplate.delete(keys);
            }
        } catch (Exception exception) {
            log.warn("Clear product cache failed", exception);
        }
    }

    @Override
    public void warmupActiveProductBases() {
        safeSet(ACTIVE_PRODUCT_BASE_LIST_KEY, toJson(loadActiveProductBasesFromDb()), baseCacheTtl());
    }

    private List<ProductBaseCacheItem> rebuildActiveBaseListWithMutex() {
        String token = tryAcquireCacheRebuildLock(ACTIVE_PRODUCT_BASE_LIST_LOCK_KEY);
        if (token != null) {
            try {
                String rechecked = stringRedisTemplate.opsForValue().get(ACTIVE_PRODUCT_BASE_LIST_KEY);
                if (rechecked != null) {
                    return parseBaseList(rechecked);
                }
                List<ProductBaseCacheItem> loaded = loadActiveProductBasesFromDb();
                safeSet(ACTIVE_PRODUCT_BASE_LIST_KEY, toJson(loaded), baseCacheTtl());
                return loaded;
            } finally {
                distributedLockService.release(ACTIVE_PRODUCT_BASE_LIST_LOCK_KEY, token);
            }
        }

        List<ProductBaseCacheItem> waited = waitForBaseListCache();
        return waited != null ? waited : loadActiveProductBasesFromDb();
    }

    private ProductBaseCacheItem rebuildBaseDetailWithMutex(Long productId) {
        String lockKey = PRODUCT_BASE_DETAIL_LOCK_KEY_PREFIX + productId + ":base";
        String token = tryAcquireCacheRebuildLock(lockKey);
        if (token != null) {
            try {
                String rechecked = stringRedisTemplate.opsForValue().get(baseDetailKey(productId));
                if (rechecked != null) {
                    return parseBaseDetail(rechecked);
                }
                ProductBaseCacheItem loaded = loadActiveProductBaseFromDb(productId);
                if (loaded == null) {
                    safeSet(baseDetailKey(productId), NULL_MARKER, nullCacheTtl());
                } else {
                    safeSet(baseDetailKey(productId), toJson(loaded), baseCacheTtl());
                }
                return loaded;
            } finally {
                distributedLockService.release(lockKey, token);
            }
        }

        ProductBaseCacheItem waited = waitForBaseDetailCache(productId);
        return waited != null ? waited : loadActiveProductBaseFromDb(productId);
    }

    private ProductStatsCacheItem rebuildStatsWithMutex(Long productId) {
        String lockKey = PRODUCT_STATS_LOCK_KEY_PREFIX + productId;
        String token = tryAcquireCacheRebuildLock(lockKey);
        if (token != null) {
            try {
                String rechecked = stringRedisTemplate.opsForValue().get(statsKey(productId));
                if (rechecked != null) {
                    return parseStats(rechecked);
                }
                ProductStatsCacheItem loaded = loadProductStatsFromDb(productId);
                if (loaded == null) {
                    safeSet(statsKey(productId), NULL_MARKER, nullCacheTtl());
                } else {
                    safeSet(statsKey(productId), toJson(loaded), statsCacheTtl());
                }
                return loaded;
            } finally {
                distributedLockService.release(lockKey, token);
            }
        }

        ProductStatsCacheItem waited = waitForStatsCache(productId);
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

    private List<ProductBaseCacheItem> parseBaseList(String cached) {
        if (NULL_MARKER.equals(cached)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(cached, BASE_LIST_TYPE);
        } catch (Exception exception) {
            safeDelete(ACTIVE_PRODUCT_BASE_LIST_KEY);
            throw new IllegalStateException("Serialize product cache failed", exception);
        }
    }

    private ProductBaseCacheItem parseBaseDetail(String cached) {
        if (NULL_MARKER.equals(cached)) {
            return null;
        }
        try {
            return objectMapper.readValue(cached, ProductBaseCacheItem.class);
        } catch (Exception exception) {
            throw new IllegalStateException("Serialize product cache failed", exception);
        }
    }

    private ProductStatsCacheItem parseStats(String cached) {
        if (NULL_MARKER.equals(cached)) {
            return null;
        }
        try {
            return objectMapper.readValue(cached, ProductStatsCacheItem.class);
        } catch (Exception exception) {
            throw new IllegalStateException("Serialize product cache failed", exception);
        }
    }

    private List<ProductBaseCacheItem> waitForBaseListCache() {
        for (int attempt = 0; attempt < 3; attempt++) {
            sleepBriefly();
            try {
                String cached = stringRedisTemplate.opsForValue().get(ACTIVE_PRODUCT_BASE_LIST_KEY);
                if (cached != null) {
                    return parseBaseList(cached);
                }
            } catch (Exception exception) {
                return null;
            }
        }
        return null;
    }

    private ProductBaseCacheItem waitForBaseDetailCache(Long productId) {
        for (int attempt = 0; attempt < 3; attempt++) {
            sleepBriefly();
            try {
                String cached = stringRedisTemplate.opsForValue().get(baseDetailKey(productId));
                if (cached != null) {
                    return parseBaseDetail(cached);
                }
            } catch (Exception exception) {
                return null;
            }
        }
        return null;
    }

    private ProductStatsCacheItem waitForStatsCache(Long productId) {
        for (int attempt = 0; attempt < 3; attempt++) {
            sleepBriefly();
            try {
                String cached = stringRedisTemplate.opsForValue().get(statsKey(productId));
                if (cached != null) {
                    return parseStats(cached);
                }
            } catch (Exception exception) {
                return null;
            }
        }
        return null;
    }

    private String tryAcquireCacheRebuildLock(String lockKey) {
        try {
            return distributedLockService.tryAcquire(lockKey, CACHE_REBUILD_WAIT_TIMEOUT, CACHE_REBUILD_LEASE_DURATION);
        } catch (Exception exception) {
            log.warn("Clear product cache failed", exception);
            return null;
        }
    }

    private void safeSet(String key, String value, Duration ttl) {
        try {
            stringRedisTemplate.opsForValue().set(key, value, ttl);
        } catch (Exception exception) {
            log.warn("Clear product cache failed", exception);
        }
    }

    private void safeDelete(String... keys) {
        if (keys == null || keys.length == 0) {
            return;
        }
        try {
            stringRedisTemplate.delete(List.of(keys));
        } catch (Exception exception) {
            log.warn("Clear product cache failed", exception);
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new IllegalStateException("Serialize product cache failed", exception);
        }
    }

    private Duration baseCacheTtl() {
        return withJitter(BASE_CACHE_TTL, BASE_CACHE_JITTER);
    }

    private Duration statsCacheTtl() {
        return withJitter(STATS_CACHE_TTL, STATS_CACHE_JITTER);
    }

    private Duration nullCacheTtl() {
        return withJitter(NULL_CACHE_TTL, NULL_CACHE_JITTER);
    }

    private Duration withJitter(Duration base, Duration jitter) {
        long baseMillis = base.toMillis();
        long jitterMillis = jitter.toMillis();
        long delta = ThreadLocalRandom.current().nextLong(jitterMillis * 2 + 1) - jitterMillis;
        long ttl = Math.max(1000L, baseMillis + delta);
        return Duration.ofMillis(ttl);
    }

    private List<Long> normalizeIds(List<Long> productIds) {
        if (productIds == null || productIds.isEmpty()) {
            return List.of();
        }
        Set<Long> normalized = new LinkedHashSet<>();
        for (Long productId : productIds) {
            if (isValidProductId(productId)) {
                normalized.add(productId);
            }
        }
        return new ArrayList<>(normalized);
    }

    private boolean isValidProductId(Long productId) {
        return productId != null && productId > 0;
    }

    private String baseDetailKey(Long productId) {
        return PRODUCT_BASE_DETAIL_KEY_PREFIX + productId + ":base";
    }

    private String statsKey(Long productId) {
        return PRODUCT_STATS_KEY_PREFIX + productId;
    }

    private void sleepBriefly() {
        try {
            Thread.sleep(60L);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }
}