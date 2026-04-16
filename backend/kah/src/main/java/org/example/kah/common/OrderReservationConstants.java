package org.example.kah.common;

import java.time.Duration;

public final class OrderReservationConstants {

    public static final String ORDER_RESERVATION_PREFIX = "order:reservation:";
    public static final String ORDER_AVAILABLE_POOL_PREFIX = ORDER_RESERVATION_PREFIX + "pool:";
    public static final String ORDER_RESERVED_POOL_PREFIX = ORDER_RESERVATION_PREFIX + "reserved:";
    public static final String ORDER_RESERVATION_ITEMS_PREFIX = ORDER_RESERVATION_PREFIX + "items:";
    public static final String ORDER_RESERVATION_META_PREFIX = ORDER_RESERVATION_PREFIX + "meta:";
    public static final String ORDER_RESERVATION_EXPIRE_INDEX_KEY = ORDER_RESERVATION_PREFIX + "expire-index";
    public static final Duration ORDER_RESERVATION_TTL = Duration.ofSeconds(45);
    public static final int ORDER_RESERVATION_RECOVERY_BATCH_SIZE = 100;

    private OrderReservationConstants() {
    }

    public static String availablePoolKey(Long productId) {
        return ORDER_AVAILABLE_POOL_PREFIX + productId;
    }

    public static String reservedPoolKey(Long productId) {
        return ORDER_RESERVED_POOL_PREFIX + productId;
    }

    public static String reservationItemsKey(String token) {
        return ORDER_RESERVATION_ITEMS_PREFIX + token;
    }

    public static String reservationMetaKey(String token) {
        return ORDER_RESERVATION_META_PREFIX + token;
    }
}