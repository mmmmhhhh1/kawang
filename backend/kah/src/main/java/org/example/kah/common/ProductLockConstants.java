package org.example.kah.common;

import java.time.Duration;

public final class ProductLockConstants {

    public static final String PRODUCT_LOCK_KEY_PREFIX = "lock:product:";
    public static final Duration PRODUCT_LOCK_WAIT_TIMEOUT = Duration.ofSeconds(3);
    public static final Duration PRODUCT_LOCK_LEASE_DURATION = Duration.ofSeconds(15);

    private ProductLockConstants() {
    }

    public static String productLockKey(Long productId) {
        return PRODUCT_LOCK_KEY_PREFIX + productId;
    }
}