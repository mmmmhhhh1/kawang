package org.example.kah.security;

import java.time.Duration;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RedisRateLimitService {

    private static final Logger log = LoggerFactory.getLogger(RedisRateLimitService.class);
    private static final DefaultRedisScript<Long> RATE_LIMIT_SCRIPT = loadRateLimitScript();

    private final StringRedisTemplate stringRedisTemplate;

    public boolean tryAcquire(String key, int limit, Duration window) {
        try {
            Long allowed = stringRedisTemplate.execute(
                    RATE_LIMIT_SCRIPT,
                    List.of(key),
                    String.valueOf(limit),
                    String.valueOf(window.toMillis()));
            return allowed == null || allowed.longValue() == 1L;
        } catch (Exception exception) {
            log.warn("Redis rate limit execution failed, allowing current request. key={}", key, exception);
            return true;
        }
    }

    private static DefaultRedisScript<Long> loadRateLimitScript() {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("lua/rate_limit_fixed_window.lua"));
        script.setResultType(Long.class);
        return script;
    }
}