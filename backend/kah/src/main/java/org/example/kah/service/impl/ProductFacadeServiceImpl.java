package org.example.kah.service.impl;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.example.kah.common.BusinessException;
import org.example.kah.common.ErrorCode;
import org.example.kah.dto.publicapi.ProductView;
import org.example.kah.entity.ProductStatus;
import org.example.kah.entity.ShopProduct;
import org.example.kah.mapper.ProductMapper;
import org.example.kah.service.ProductFacadeService;
import org.example.kah.service.impl.base.AbstractServiceSupport;
import org.springframework.stereotype.Service;

/**
 * {@link ProductFacadeService} 的默认实现。
 * 只负责公开商品的读取与视图映射。
 */
@Service
@RequiredArgsConstructor
public class ProductFacadeServiceImpl extends AbstractServiceSupport implements ProductFacadeService {

    private final ProductMapper productMapper;

    /**
     * 查询前台可售商品列表。
     */
    @Override
    public List<ProductView> listProducts() {
        return productMapper.findActiveProducts().stream().map(this::toView).toList();
    }

    /**
     * 查询单个商品详情。
     */
    @Override
    public ProductView getProduct(Long id) {
        ShopProduct product = productMapper.findById(id);
        if (product == null || !ProductStatus.ACTIVE.equals(product.getStatus())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "商品不存在");
        }
        return toView(product);
    }

    /**
     * 将商品实体映射为前台视图。
     */
    private ProductView toView(ShopProduct product) {
        return new ProductView(
                product.getId(),
                product.getSku(),
                product.getTitle(),
                product.getVendor(),
                product.getPlanName(),
                product.getDescription(),
                product.getPrice(),
                product.getAvailableStock(),
                product.getSoldCount());
    }
}
