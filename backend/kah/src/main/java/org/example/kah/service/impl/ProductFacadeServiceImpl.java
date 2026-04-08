package org.example.kah.service.impl;

import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.example.kah.cache.ProductBaseCacheItem;
import org.example.kah.cache.ProductStatsCacheItem;
import org.example.kah.common.BusinessException;
import org.example.kah.common.ErrorCode;
import org.example.kah.dto.publicapi.ProductView;
import org.example.kah.service.ProductCacheService;
import org.example.kah.service.ProductFacadeService;
import org.example.kah.service.impl.base.AbstractServiceSupport;
import org.springframework.stereotype.Service;

/**
 * {@link ProductFacadeService} 的默认实现。
 * 通过“基础信息缓存 + 统计缓存”组装前台商品视图，减少高频商品展示对数据库的直接压力。
 */
@Service
@RequiredArgsConstructor
public class ProductFacadeServiceImpl extends AbstractServiceSupport implements ProductFacadeService {

    private final ProductCacheService productCacheService;

    /** 查询前台可售商品列表。 */
    @Override
    public List<ProductView> listProducts() {
        List<ProductBaseCacheItem> baseItems = productCacheService.getActiveProductBases();
        Map<Long, ProductStatsCacheItem> statsMap = productCacheService.getProductStats(baseItems.stream().map(ProductBaseCacheItem::id).toList());
        return baseItems.stream()
                .map(item -> toView(item, statsMap.get(item.id())))
                .toList();
    }

    /** 查询单个商品详情。 */
    @Override
    public ProductView getProduct(Long id) {
        require(id != null && id > 0, ErrorCode.BAD_REQUEST, "商品 ID 非法");
        ProductBaseCacheItem baseItem = productCacheService.getActiveProductBase(id);
        if (baseItem == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "商品不存在");
        }
        return toView(baseItem, productCacheService.getProductStats(id));
    }

    /** 将缓存对象映射为前台商品视图。 */
    private ProductView toView(ProductBaseCacheItem baseItem, ProductStatsCacheItem statsItem) {
        int availableStock = statsItem == null || statsItem.availableStock() == null ? 0 : statsItem.availableStock();
        int soldCount = statsItem == null || statsItem.soldCount() == null ? 0 : statsItem.soldCount();
        return new ProductView(
                baseItem.id(),
                baseItem.sku(),
                baseItem.title(),
                baseItem.vendor(),
                baseItem.planName(),
                baseItem.description(),
                baseItem.price(),
                availableStock,
                soldCount);
    }
}