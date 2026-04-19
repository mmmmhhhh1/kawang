package org.example.kah.service.impl;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.example.kah.common.BusinessException;
import org.example.kah.common.CursorPageResponse;
import org.example.kah.common.ErrorCode;
import org.example.kah.dto.admin.AdminProductOptionView;
import org.example.kah.dto.admin.AdminProductSaveRequest;
import org.example.kah.dto.admin.AdminProductView;
import org.example.kah.entity.ProductStatus;
import org.example.kah.entity.ShopProduct;
import org.example.kah.mapper.ProductAccountMapper;
import org.example.kah.mapper.ProductMapper;
import org.example.kah.mapper.ShopOrderMapper;
import org.example.kah.service.AdminProductService;
import org.example.kah.service.OrderReservationService;
import org.example.kah.service.ProductCacheRefreshService;
import org.example.kah.service.ProductLockExecutorService;
import org.example.kah.service.impl.base.AbstractCrudService;
import org.example.kah.util.CursorCodecUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AdminProductServiceImpl extends AbstractCrudService<ShopProduct, Long> implements AdminProductService {

    private static final int ADMIN_PAGE_LIMIT = 50;
    private static final int OPTION_LIMIT = 50;

    private final ProductMapper productMapper;
    private final ProductAccountMapper productAccountMapper;
    private final ShopOrderMapper shopOrderMapper;
    private final ProductCacheRefreshService productCacheRefreshService;
    private final ProductLockExecutorService productLockExecutorService;
    private final OrderReservationService orderReservationService;

    @Override
    public CursorPageResponse<AdminProductView> page(int size, String cursor, String keyword, String status) {
        return queryPage(size, cursor, keyword, status, ADMIN_PAGE_LIMIT);
    }

    @Override
    public List<AdminProductOptionView> searchOptions(String keyword, int size) {
        return queryPage(size, null, keyword, null, OPTION_LIMIT).items().stream()
                .map(item -> new AdminProductOptionView(item.id(), item.title(), item.sku(), item.status()))
                .toList();
    }

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
        productCacheRefreshService.refreshBaseAfterWrite(product.getId());
        productCacheRefreshService.refreshStatsAfterWrite(product.getId());
        if (ProductStatus.ACTIVE.equals(product.getStatus())) {
            orderReservationService.rebuildProductPool(product.getId());
        } else {
            orderReservationService.removeProductPool(product.getId());
        }
        return toView(productMapper.findById(product.getId()));
    }

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
        productCacheRefreshService.refreshBaseAfterWrite(id);
        return toView(productMapper.findById(id));
    }

    @Override
    public AdminProductView updateStatus(Long id, String status) {
        ensureStatus(status);
        requireById(id);
        return productLockExecutorService.execute(id, () -> {
            productMapper.updateStatus(id, status);
            return toView(productMapper.findById(id));
        }, () -> refreshProductState(id, status));
    }

    @Override
    public void delete(Long id) {
        productLockExecutorService.execute(id, () -> doDelete(id), () -> {
            productCacheRefreshService.removeProductAfterDelete(id);
            orderReservationService.removeProductPool(id);
        });
    }

    @Override
    protected ShopProduct findEntityById(Long id) {
        return productMapper.findById(id);
    }

    @Override
    protected String entityLabel() {
        return "商品";
    }

    private CursorPageResponse<AdminProductView> queryPage(int size, String cursor, String keyword, String status, int maxSize) {
        int safeSize = normalizeSize(size, maxSize);
        CursorCodecUtils.DecodedCursor decodedCursor = CursorCodecUtils.decode(cursor);
        Map<String, Object> params = new HashMap<>();
        params.put("status", trim(status));
        params.put("keyword", trim(keyword));
        params.put("limit", safeSize + 1);
        if (decodedCursor != null) {
            params.put("cursorCreatedAt", decodedCursor.createdAt());
            params.put("cursorId", decodedCursor.id());
        }
        List<ShopProduct> rows = productMapper.findAdminCursorPage(params);
        boolean hasMore = rows.size() > safeSize;
        List<ShopProduct> pageItems = hasMore ? rows.subList(0, safeSize) : rows;
        String nextCursor = hasMore ? CursorCodecUtils.encode(pageItems.get(pageItems.size() - 1).getCreatedAt(), pageItems.get(pageItems.size() - 1).getId()) : null;
        return new CursorPageResponse<>(pageItems.stream().map(this::toView).toList(), nextCursor, hasMore);
    }

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

    private void doDelete(Long id) {
        requireById(id);
        long orderCount = shopOrderMapper.countByProductId(id);
        if (orderCount > 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "商品已有历史订单，不能删除");
        }
        productAccountMapper.deleteByProductId(id);
        productMapper.deleteById(id);
    }

    private void refreshProductState(Long productId, String status) {
        productCacheRefreshService.refreshBaseAfterWrite(productId);
        productCacheRefreshService.refreshStatsAfterWrite(productId);
        if (ProductStatus.ACTIVE.equals(status)) {
            orderReservationService.rebuildProductPool(productId);
        } else {
            orderReservationService.removeProductPool(productId);
        }
    }

    private AdminProductView toView(ShopProduct product) {
        return new AdminProductView(product.getId(), product.getSku(), product.getTitle(), product.getVendor(), product.getPlanName(), product.getDescription(), product.getPrice(), product.getAvailableStock(), product.getSoldCount(), product.getStatus(), product.getSortOrder(), product.getUpdatedAt());
    }

    private void ensureStatus(String status) {
        if (!ProductStatus.ACTIVE.equals(status) && !ProductStatus.INACTIVE.equals(status)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "商品状态不合法");
        }
    }
}