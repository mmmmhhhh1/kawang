package org.example.kah.service.impl;

import java.time.Duration;
import java.util.Collections;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.example.kah.service.DistributedLockService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

/**
 * {@link DistributedLockService} 的 Redis 实现。
 * 使用 SET NX EX 获取锁，并通过 Lua 脚本确保只有锁持有者才能释放。
 */
@Service
@RequiredArgsConstructor
public class RedisDistributedLockServiceImpl implements DistributedLockService {

    private static final Logger log = LoggerFactory.getLogger(RedisDistributedLockServiceImpl.class);

    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end",
            Long.class);

    private final StringRedisTemplate stringRedisTemplate;

    @Override
    public String tryAcquire(String key, Duration waitTimeout, Duration leaseDuration) {
        long deadline = System.nanoTime() + waitTimeout.toNanos();
        String token = UUID.randomUUID().toString();
        while (System.nanoTime() <= deadline) {
            try {
                Boolean locked = stringRedisTemplate.opsForValue().setIfAbsent(key, token, leaseDuration);
                if (Boolean.TRUE.equals(locked)) {
                    return token;
                }
            } catch (Exception exception) {
                throw new IllegalStateException("Redis 分布式锁获取失败", exception);
            }

            try {
                Thread.sleep(50L);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("等待 Redis 锁时被中断", exception);
            }
        }
        return null;
    }

    @Override
    public void release(String key, String token) {
        if (key == null || key.isBlank() || token == null || token.isBlank()) {
            return;
        }
        try {
            stringRedisTemplate.execute(UNLOCK_SCRIPT, Collections.singletonList(key), token);
        } catch (Exception exception) {
            log.warn("释放 Redis 锁失败，key={}", key, exception);
        }
    }
}