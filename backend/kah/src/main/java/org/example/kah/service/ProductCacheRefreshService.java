package org.example.kah.service;

/**
 * 商品缓存刷新策略服务接口。
 * 对外提供“写后刷新/失败降级”的一致性包装，避免业务层散落大量缓存异常处理。
 */
public interface ProductCacheRefreshService {

    /** 商品基础信息写成功后刷新基础缓存。 */
    void refreshBaseAfterWrite(Long productId);

    /** 商品库存销量写成功后刷新统计缓存。 */
    void refreshStatsAfterWrite(Long productId);

    /** 商品删除成功后移除全部相关缓存。 */
    void removeProductAfterDelete(Long productId);

    /** 启动时清理旧商品缓存并预热活动商品列表缓存。 */
    void resetAndWarmupProductCaches();
}