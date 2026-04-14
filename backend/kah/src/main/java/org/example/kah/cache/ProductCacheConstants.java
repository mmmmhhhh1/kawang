package org.example.kah.cache;

import java.time.Duration;

/**
 * 商品缓存相关常量。
 * 统一维护缓存 key、重建锁 key、空值标记和缓存时间，避免散落在实现类中。
 */
public final class ProductCacheConstants {

    /** 商品缓存总前缀。 */
    public static final String PRODUCT_CACHE_KEY_PREFIX = "product:";

    /** 前台可售商品基础信息列表缓存 key。 */
    public static final String ACTIVE_PRODUCT_BASE_LIST_KEY = PRODUCT_CACHE_KEY_PREFIX + "list:active:base";

    /** 单商品基础信息缓存 key 前缀。 */
    public static final String PRODUCT_BASE_DETAIL_KEY_PREFIX = PRODUCT_CACHE_KEY_PREFIX + "detail:";

    /** 单商品库存与销量统计缓存 key 前缀。 */
    public static final String PRODUCT_STATS_KEY_PREFIX = PRODUCT_CACHE_KEY_PREFIX + "stats:";

    /** 不存在商品或下架商品的空值占位符。 */
    public static final String NULL_MARKER = "__NULL__";

    /** 活动商品基础列表缓存重建锁 key。 */
    public static final String ACTIVE_PRODUCT_BASE_LIST_LOCK_KEY = "lock:cache:product:list:active:base";

    /** 单商品基础信息缓存重建锁 key 前缀。 */
    public static final String PRODUCT_BASE_DETAIL_LOCK_KEY_PREFIX = "lock:cache:product:detail:";

    /** 单商品统计缓存重建锁 key 前缀。 */
    public static final String PRODUCT_STATS_LOCK_KEY_PREFIX = "lock:cache:product:stats:";

    /** 基础信息缓存的基础过期时间。 */
    public static final Duration BASE_CACHE_TTL = Duration.ofMinutes(30);

    /** 基础信息缓存的随机抖动窗口。 */
    public static final Duration BASE_CACHE_JITTER = Duration.ofMinutes(5);

    /** 统计缓存的基础过期时间。 */
    public static final Duration STATS_CACHE_TTL = Duration.ofSeconds(60);

    /** 统计缓存的随机抖动窗口。 */
    public static final Duration STATS_CACHE_JITTER = Duration.ofSeconds(15);

    /** 空值缓存的基础过期时间。 */
    public static final Duration NULL_CACHE_TTL = Duration.ofMinutes(2);

    /** 空值缓存的随机抖动窗口。 */
    public static final Duration NULL_CACHE_JITTER = Duration.ofSeconds(30);

    /** 缓存重建锁的最大等待时间。 */
    public static final Duration CACHE_REBUILD_WAIT_TIMEOUT = Duration.ofMillis(250);

    /** 缓存重建锁的租约时间。 */
    public static final Duration CACHE_REBUILD_LEASE_DURATION = Duration.ofSeconds(5);

    private ProductCacheConstants() {
    }

    /** 生成单商品基础信息缓存 key。 */
    public static String baseDetailKey(Long productId) {
        return PRODUCT_BASE_DETAIL_KEY_PREFIX + productId + ":base";
    }

    /** 生成单商品统计缓存 key。 */
    public static String statsKey(Long productId) {
        return PRODUCT_STATS_KEY_PREFIX + productId;
    }

    /** 生成单商品基础信息缓存重建锁 key。 */
    public static String baseDetailLockKey(Long productId) {
        return PRODUCT_BASE_DETAIL_LOCK_KEY_PREFIX + productId + ":base";
    }

    /** 生成单商品统计缓存重建锁 key。 */
    public static String statsLockKey(Long productId) {
        return PRODUCT_STATS_LOCK_KEY_PREFIX + productId;
    }
}
