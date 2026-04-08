package org.example.kah.service;

import java.time.Duration;

/**
 * Redis 分布式锁服务接口。
 * 用于商品级写路径串行化和热点缓存重建互斥控制。
 */
public interface DistributedLockService {

    /**
     * 在指定等待时间内尝试获取分布式锁。
     *
     * @param key 锁 key
     * @param waitTimeout 最大等待时长
     * @param leaseDuration 锁自动过期时长
     * @return 锁 token，拿不到锁时返回 {@code null}
     */
    String tryAcquire(String key, Duration waitTimeout, Duration leaseDuration);

    /**
     * 释放分布式锁。
     *
     * @param key 锁 key
     * @param token 当前持有者 token
     */
    void release(String key, String token);
}