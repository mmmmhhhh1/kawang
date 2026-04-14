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
    private static final String BUSY_MESSAGE = "\u5546\u54c1\u64cd\u4f5c\u7e41\u5fd9\uff0c\u8bf7\u7a0d\u540e\u518d\u8bd5";

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
                log.warn(
                        "\u83b7\u53d6\u5546\u54c1\u5206\u5e03\u5f0f\u9501\u5931\u8d25\uff0c\u964d\u7ea7\u4e3a\u4ec5\u4f9d\u8d56\u672c\u673a\u9501\u548c\u6570\u636e\u5e93\u4e8b\u52a1\uff0cproductId={}",
                        productId,
                        exception);
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
            throw new IllegalStateException("\u7b49\u5f85\u5546\u54c1\u672c\u673a\u9501\u65f6\u88ab\u4e2d\u65ad", exception);
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