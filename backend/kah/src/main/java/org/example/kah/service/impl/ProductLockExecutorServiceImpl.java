package org.example.kah.service.impl;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.example.kah.common.BusinessException;
import org.example.kah.common.ErrorCode;
import org.example.kah.common.ProductLockConstants;
import org.example.kah.service.DistributedLockService;
import org.example.kah.service.ProductLockExecutorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@RequiredArgsConstructor
public class ProductLockExecutorServiceImpl implements ProductLockExecutorService {

    private static final Logger log = LoggerFactory.getLogger(ProductLockExecutorServiceImpl.class);
    private static final String BUSY_MESSAGE = "商品操作繁忙，请稍后再试";

    private final DistributedLockService distributedLockService;
    private final TransactionTemplate transactionTemplate;
    private final ConcurrentMap<Long, ReentrantLock> localLocks = new ConcurrentHashMap<>();

    @Override
    public <T> T execute(Long productId, Supplier<T> transactionalAction, Runnable afterCommitAction) {
        ReentrantLock localLock = acquireLocalLock(productId);
        String lockKey = ProductLockConstants.productLockKey(productId);
        String token = null;
        boolean distributedLocked = false;
        try {
            try {
                token = distributedLockService.tryAcquire(
                        lockKey,
                        ProductLockConstants.PRODUCT_LOCK_WAIT_TIMEOUT,
                        ProductLockConstants.PRODUCT_LOCK_LEASE_DURATION);
                if (token == null) {
                    throw new BusinessException(ErrorCode.BAD_REQUEST, BUSY_MESSAGE);
                }
                distributedLocked = true;
            } catch (BusinessException exception) {
                throw exception;
            } catch (Exception exception) {
                log.warn("获取商品分布式锁失败，降级为仅依赖本机锁和数据库事务，productId={}", productId, exception);
            }

            T result = transactionTemplate.execute(status -> transactionalAction.get());
            if (afterCommitAction != null) {
                afterCommitAction.run();
            }
            return result;
        } finally {
            if (distributedLocked) {
                distributedLockService.release(lockKey, token);
            }
            releaseLocalLock(productId, localLock);
        }
    }

    @Override
    public void execute(Long productId, Runnable transactionalAction, Runnable afterCommitAction) {
        execute(productId, () -> {
            transactionalAction.run();
            return null;
        }, afterCommitAction);
    }

    private ReentrantLock acquireLocalLock(Long productId) {
        ReentrantLock localLock = localLocks.computeIfAbsent(productId, ignored -> new ReentrantLock(true));
        try {
            boolean locked = localLock.tryLock(
                    ProductLockConstants.PRODUCT_LOCK_WAIT_TIMEOUT.toMillis(),
                    TimeUnit.MILLISECONDS);
            if (!locked) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, BUSY_MESSAGE);
            }
            return localLock;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("等待商品本机锁时被中断", exception);
        }
    }

    private void releaseLocalLock(Long productId, ReentrantLock localLock) {
        try {
            localLock.unlock();
        } finally {
            if (!localLock.hasQueuedThreads()) {
                localLocks.remove(productId, localLock);
            }
        }
    }
}
