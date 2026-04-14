package org.example.kah.util;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 缓存 TTL 工具。
 * 负责为固定过期时间增加随机抖动，降低同一时刻批量失效带来的雪崩风险。
 */
public final class CacheTtlUtils {

    private CacheTtlUtils() {
    }

    /**
     * 为给定 TTL 增加正负抖动。
     *
     * @param base 基础 TTL
     * @param jitter 抖动窗口
     * @return 带随机抖动的最终 TTL，最小不低于 1 秒
     */
    public static Duration withJitter(Duration base, Duration jitter) {
        long baseMillis = base.toMillis();
        long jitterMillis = jitter.toMillis();
        long delta = ThreadLocalRandom.current().nextLong(jitterMillis * 2 + 1) - jitterMillis;
        long ttl = Math.max(1000L, baseMillis + delta);
        return Duration.ofMillis(ttl);
    }
}
