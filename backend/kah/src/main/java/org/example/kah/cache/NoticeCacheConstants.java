package org.example.kah.cache;

import java.time.Duration;

public final class NoticeCacheConstants {

    public static final String NOTICE_CACHE_KEY_PREFIX = "notice:";
    public static final String PUBLISHED_NOTICE_LIST_KEY = NOTICE_CACHE_KEY_PREFIX + "list:published";
    public static final Duration PUBLISHED_NOTICE_TTL = Duration.ofMinutes(30);
    public static final Duration PUBLISHED_NOTICE_JITTER = Duration.ofMinutes(5);

    private NoticeCacheConstants() {
    }
}
