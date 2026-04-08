package org.example.kah.service.impl;

import lombok.RequiredArgsConstructor;
import org.example.kah.service.ProductCacheRefreshService;
import org.example.kah.service.ProductCacheService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * {@link ProductCacheRefreshService} 的默认实现。
 * 把商品缓存刷新、删除和异常降级策略集中在一个地方，便于业务服务复用。
 */
@Service
@RequiredArgsConstructor
public class ProductCacheRefreshServiceImpl implements ProductCacheRefreshService {

    private static final Logger log = LoggerFactory.getLogger(ProductCacheRefreshServiceImpl.class);

    private final ProductCacheService productCacheService;

    @Override
    public void refreshBaseAfterWrite(Long productId) {
        try {
            productCacheService.refreshProductBase(productId);
        } catch (Exception exception) {
            log.warn("刷新商品基础缓存失败，productId={}", productId, exception);
            productCacheService.evictProductBase(productId);
        }
    }

    @Override
    public void refreshStatsAfterWrite(Long productId) {
        try {
            productCacheService.refreshProductStats(productId);
        } catch (Exception exception) {
            log.warn("刷新商品统计缓存失败，productId={}", productId, exception);
            productCacheService.evictProductStats(productId);
        }
    }

    @Override
    public void removeProductAfterDelete(Long productId) {
        try {
            productCacheService.removeProduct(productId);
        } catch (Exception exception) {
            log.warn("删除商品缓存失败，productId={}", productId, exception);
            productCacheService.evictProductBase(productId);
            productCacheService.evictProductStats(productId);
        }
    }

    @Override
    public void resetAndWarmupProductCaches() {
        try {
            productCacheService.clearAllProductCaches();
            productCacheService.warmupActiveProductBases();
        } catch (Exception exception) {
            log.warn("重置并预热商品缓存失败", exception);
        }
    }
}