package org.example.kah.service;

import java.util.function.Supplier;

/**
 * 商品级写操作执行器接口。
 * 统一封装分布式锁、数据库事务和事务提交后的缓存刷新。
 */
public interface ProductLockExecutorService {

    /** 在商品级锁保护下执行写操作，并在事务提交后执行缓存回调。 */
    <T> T execute(Long productId, Supplier<T> transactionalAction, Runnable afterCommitAction);

    /** 在商品级锁保护下执行无返回值写操作，并在事务提交后执行缓存回调。 */
    void execute(Long productId, Runnable transactionalAction, Runnable afterCommitAction);
}