package org.example.kah.service.impl;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.example.kah.common.BusinessException;
import org.example.kah.common.ErrorCode;
import org.example.kah.dto.admin.AdminProductSaveRequest;
import org.example.kah.dto.admin.AdminProductView;
import org.example.kah.entity.ProductStatus;
import org.example.kah.entity.ShopProduct;
import org.example.kah.mapper.ProductAccountMapper;
import org.example.kah.mapper.ProductMapper;
import org.example.kah.mapper.ShopOrderMapper;
import org.example.kah.service.AdminProductService;
import org.example.kah.service.impl.base.AbstractCrudService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@link AdminProductService} 的默认实现。
 * 负责后台商品的增删改查、状态校验和删除前的历史订单保护。
 */
@Service
@RequiredArgsConstructor
public class AdminProductServiceImpl extends AbstractCrudService<ShopProduct, Long> implements AdminProductService {

    private final ProductMapper productMapper;
    private final ProductAccountMapper productAccountMapper;
    private final ShopOrderMapper shopOrderMapper;

    /** 读取后台商品列表。 */
    @Override
    public List<AdminProductView> list() {
        return productMapper.findAllProducts().stream().map(this::toView).toList();
    }

    /** 创建商品并初始化库存统计字段。 */
    @Override
    public AdminProductView create(AdminProductSaveRequest request) {
        ensureStatus(request.status());
        if (productMapper.findBySku(trim(request.sku())) != null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "SKU 已存在");
        }
        ShopProduct product = new ShopProduct();
        fillProduct(product, request);
        product.setAvailableStock(0);
        product.setSoldCount(0);
        try {
            productMapper.insert(product);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "SKU 已存在");
        }
        return toView(productMapper.findById(product.getId()));
    }

    /** 更新商品基础信息。 */
    @Override
    public AdminProductView update(Long id, AdminProductSaveRequest request) {
        ensureStatus(request.status());
        ShopProduct existing = requireById(id);
        ShopProduct skuOwner = productMapper.findBySku(trim(request.sku()));
        if (skuOwner != null && !skuOwner.getId().equals(id)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "SKU 已存在");
        }
        fillProduct(existing, request);
        productMapper.update(existing);
        return toView(productMapper.findById(id));
    }

    /** 切换商品上下架状态。 */
    @Override
    public AdminProductView updateStatus(Long id, String status) {
        ensureStatus(status);
        requireById(id);
        productMapper.updateStatus(id, status);
        return toView(productMapper.findById(id));
    }

    /**
     * 删除商品。
     * 已有关联订单的商品不允许删除；没有历史订单的商品会连同卡密池一起删除。
     */
    @Override
    @Transactional
    public void delete(Long id) {
        requireById(id);
        long orderCount = shopOrderMapper.countByProductId(id);
        if (orderCount > 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "商品已有历史订单，不能删除");
        }
        productAccountMapper.deleteByProductId(id);
        productMapper.deleteById(id);
    }

    /** 按主键查询商品实体。 */
    @Override
    protected ShopProduct findEntityById(Long id) {
        return productMapper.findById(id);
    }

    /** 返回实体名称供异常提示复用。 */
    @Override
    protected String entityLabel() {
        return "商品";
    }

    /** 将请求字段回填到商品实体。 */
    private void fillProduct(ShopProduct product, AdminProductSaveRequest request) {
        product.setSku(trim(request.sku()));
        product.setTitle(trim(request.title()));
        product.setVendor(trim(request.vendor()));
        product.setPlanName(trim(request.planName()));
        product.setDescription(trim(request.description()));
        product.setPrice(request.price());
        product.setStatus(trim(request.status()));
        product.setSortOrder(request.sortOrder());
    }

    /** 将实体映射为后台商品视图。 */
    private AdminProductView toView(ShopProduct product) {
        return new AdminProductView(
                product.getId(),
                product.getSku(),
                product.getTitle(),
                product.getVendor(),
                product.getPlanName(),
                product.getDescription(),
                product.getPrice(),
                product.getAvailableStock(),
                product.getSoldCount(),
                product.getStatus(),
                product.getSortOrder(),
                product.getUpdatedAt());
    }

    /** 校验商品状态是否合法。 */
    private void ensureStatus(String status) {
        if (!ProductStatus.ACTIVE.equals(status) && !ProductStatus.INACTIVE.equals(status)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "商品状态非法");
        }
    }
}