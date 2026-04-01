package org.example.kah.service.impl;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.example.kah.common.BusinessException;
import org.example.kah.common.ErrorCode;
import org.example.kah.common.PageResponse;
import org.example.kah.dto.admin.AdminOrderDetailView;
import org.example.kah.dto.admin.AdminOrderItemView;
import org.example.kah.entity.AccountStatus;
import org.example.kah.entity.OrderStatus;
import org.example.kah.entity.ProductAccount;
import org.example.kah.entity.ShopOrder;
import org.example.kah.mapper.ProductAccountMapper;
import org.example.kah.mapper.ProductMapper;
import org.example.kah.mapper.ShopOrderAccountMapper;
import org.example.kah.mapper.ShopOrderMapper;
import org.example.kah.service.AdminOrderService;
import org.example.kah.service.impl.base.AbstractCrudService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@link AdminOrderService} 的默认实现。
 * 负责后台订单分页查询、详情查看和关闭订单后的库存回滚。
 */
@Service
@RequiredArgsConstructor
public class AdminOrderServiceImpl extends AbstractCrudService<ShopOrder, Long> implements AdminOrderService {

    private final ShopOrderMapper shopOrderMapper;
    private final ShopOrderAccountMapper shopOrderAccountMapper;
    private final ProductAccountMapper productAccountMapper;
    private final ProductMapper productMapper;

    /**
     * 分页查询订单列表。
     */
    @Override
    public PageResponse<AdminOrderItemView> list(int page, int size, String status, Long productId, String keyword) {
        int safePage = normalizePage(page);
        int safeSize = normalizeSize(size, 50);
        Map<String, Object> params = new HashMap<>();
        params.put("status", trim(status));
        params.put("productId", productId);
        params.put("keyword", trim(keyword));
        params.put("size", safeSize);
        params.put("offset", (safePage - 1) * safeSize);
        List<AdminOrderItemView> items = shopOrderMapper.findPage(params).stream().map(this::toItemView).toList();
        long total = shopOrderMapper.countPage(params);
        return new PageResponse<>(items, total, safePage, safeSize);
    }

    /**
     * 查询订单详情以及该订单绑定的脱敏账号快照。
     */
    @Override
    public AdminOrderDetailView detail(Long id) {
        ShopOrder order = requireById(id);
        List<String> accounts = shopOrderAccountMapper.findByOrderId(id).stream()
                .map(item -> item.getMaskedAccountSnapshot())
                .toList();
        return toDetailView(order, accounts);
    }

    /**
     * 关闭成功订单并释放已分配账号。
     * 这里会锁定订单和账号池记录，避免关闭过程中出现并发状态漂移。
     */
    @Override
    @Transactional
    public AdminOrderDetailView close(Long id, String reason) {
        ShopOrder order = shopOrderMapper.lockById(id);
        if (order == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "订单不存在");
        }
        if (!OrderStatus.SUCCESS.equals(order.getStatus())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "仅成功订单可关闭");
        }

        List<ProductAccount> accounts = productAccountMapper.lockByAssignedOrderId(id);
        for (ProductAccount account : accounts) {
            if (AccountStatus.ASSIGNED.equals(account.getStatus())) {
                productAccountMapper.release(account.getId());
            }
        }

        shopOrderMapper.close(id, trim(reason));
        productMapper.syncStats();
        return detail(id);
    }

    /**
     * 按主键查询订单实体。
     */
    @Override
    protected ShopOrder findEntityById(Long id) {
        return shopOrderMapper.findById(id);
    }

    /**
     * 返回实体名称供异常提示复用。
     */
    @Override
    protected String entityLabel() {
        return "订单";
    }

    /**
     * 将订单实体映射为后台列表视图。
     */
    private AdminOrderItemView toItemView(ShopOrder order) {
        return new AdminOrderItemView(
                order.getId(),
                order.getOrderNo(),
                order.getProductId(),
                order.getProductTitleSnapshot(),
                order.getQuantity(),
                order.getUnitPrice(),
                order.getTotalAmount(),
                order.getBuyerName(),
                order.getBuyerContact(),
                order.getBuyerRemark(),
                order.getStatus(),
                order.getClosedReason(),
                order.getCreatedAt());
    }

    /**
     * 将订单实体和账号快照组合成详情视图。
     */
    private AdminOrderDetailView toDetailView(ShopOrder order, List<String> accounts) {
        return new AdminOrderDetailView(
                order.getId(),
                order.getOrderNo(),
                order.getProductId(),
                order.getProductTitleSnapshot(),
                order.getQuantity(),
                order.getUnitPrice(),
                order.getTotalAmount(),
                order.getBuyerName(),
                order.getBuyerContact(),
                order.getBuyerRemark(),
                order.getStatus(),
                order.getClosedReason(),
                order.getCreatedAt(),
                accounts);
    }
}
