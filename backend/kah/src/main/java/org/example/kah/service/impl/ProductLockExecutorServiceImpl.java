package org.example.kah.service.impl;

import java.time.Duration;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.example.kah.common.BusinessException;
import org.example.kah.common.ErrorCode;
import org.example.kah.service.DistributedLockService;
import org.example.kah.service.ProductLockExecutorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * {@link ProductLockExecutorService} 的默认实现。
 * 统一收口商品级分布式锁、数据库事务和事务提交后的缓存回调。
 */
@Service
@RequiredArgsConstructor
public class ProductLockExecutorServiceImpl implements ProductLockExecutorService {

    private static final Logger log = LoggerFactory.getLogger(ProductLockExecutorServiceImpl.class);

    private static final Duration PRODUCT_LOCK_WAIT_TIMEOUT = Duration.ofSeconds(3);
    private static final Duration PRODUCT_LOCK_LEASE_DURATION = Duration.ofSeconds(15);
    private static final String PRODUCT_LOCK_KEY_PREFIX = "lock:product:";

    private final DistributedLockService distributedLockService;
    private final TransactionTemplate transactionTemplate;

    @Override
    public <T> T execute(Long productId, Supplier<T> transactionalAction, Runnable afterCommitAction) {
        String lockKey = PRODUCT_LOCK_KEY_PREFIX + productId;
        String token = null;
        boolean locked = false;
        try {
            token = distributedLockService.tryAcquire(lockKey, PRODUCT_LOCK_WAIT_TIMEOUT, PRODUCT_LOCK_LEASE_DURATION);
            if (token == null) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "商品操作繁忙，请稍后再试");
            }
            locked = true;
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            log.warn("获取商品分布式锁失败，降级为仅依赖数据库事务，productId={}", productId, exception);
        }

        try {
            T result = transactionTemplate.execute(status -> transactionalAction.get());
            if (afterCommitAction != null) {
                afterCommitAction.run();
            }
            return result;
        } finally {
            if (locked) {
                distributedLockService.release(lockKey, token);
            }
        }
    }

    @Override
    public void execute(Long productId, Runnable transactionalAction, Runnable afterCommitAction) {
        execute(productId, () -> {
            transactionalAction.run();
            return null;
        }, afterCommitAction);
    }
}