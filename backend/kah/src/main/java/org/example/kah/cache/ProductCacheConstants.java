package org.example.kah.cache;

import java.time.Duration;

public final class ProductCacheConstants {

    public static final String PRODUCT_CACHE_KEY_PREFIX = "product:";
    public static final String ACTIVE_PRODUCT_BASE_LIST_KEY = PRODUCT_CACHE_KEY_PREFIX + "list:active:base";
    public static final String PRODUCT_BASE_DETAIL_KEY_PREFIX = PRODUCT_CACHE_KEY_PREFIX + "detail:";
    public static final String PRODUCT_STATS_KEY_PREFIX = PRODUCT_CACHE_KEY_PREFIX + "stats:";
    public static final String NULL_MARKER = "__NULL__";

    public static final String ACTIVE_PRODUCT_BASE_LIST_LOCK_KEY = "lock:cache:product:list:active:base";
    public static final String PRODUCT_BASE_DETAIL_LOCK_KEY_PREFIX = "lock:cache:product:detail:";
    public static final String PRODUCT_STATS_LOCK_KEY_PREFIX = "lock:cache:product:stats:";

    public static final Duration STATS_CACHE_TTL = Duration.ofSeconds(60);
    public static final Duration STATS_CACHE_JITTER = Duration.ofSeconds(15);
    public static final Duration NULL_CACHE_TTL = Duration.ofMinutes(2);
    public static final Duration NULL_CACHE_JITTER = Duration.ofSeconds(30);
    public static final Duration CACHE_REBUILD_WAIT_TIMEOUT = Duration.ofMillis(250);
    public static final Duration CACHE_REBUILD_LEASE_DURATION = Duration.ofSeconds(5);

    public static final Duration BASE_LOCAL_CACHE_TTL = Duration.ofMinutes(10);
    public static final Duration STATS_LOCAL_CACHE_TTL = Duration.ofSeconds(15);
    public static final long BASE_LOCAL_CACHE_MAXIMUM_SIZE = 512;
    public static final long STATS_LOCAL_CACHE_MAXIMUM_SIZE = 2048;

    private ProductCacheConstants() {
    }

    public static String baseDetailKey(Long productId) {
        return PRODUCT_BASE_DETAIL_KEY_PREFIX + productId + ":base";
    }

    public static String statsKey(Long productId) {
        return PRODUCT_STATS_KEY_PREFIX + productId;
    }

    public static String baseDetailLockKey(Long productId) {
        return PRODUCT_BASE_DETAIL_LOCK_KEY_PREFIX + productId + ":base";
    }

    public static String statsLockKey(Long productId) {
        return PRODUCT_STATS_LOCK_KEY_PREFIX + productId;
    }
}
