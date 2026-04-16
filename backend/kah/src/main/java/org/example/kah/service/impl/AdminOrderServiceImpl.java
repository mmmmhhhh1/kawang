package org.example.kah.service.impl;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import lombok.RequiredArgsConstructor;
import org.example.kah.common.BusinessException;
import org.example.kah.common.CursorPageResponse;
import org.example.kah.common.ErrorCode;
import org.example.kah.dto.admin.AdminOrderCardKeyView;
import org.example.kah.dto.admin.AdminOrderDetailView;
import org.example.kah.dto.admin.AdminOrderItemView;
import org.example.kah.entity.EnableStatus;
import org.example.kah.entity.MemberUser;
import org.example.kah.entity.OrderStatus;
import org.example.kah.entity.PaymentMethod;
import org.example.kah.entity.ProductAccount;
import org.example.kah.entity.ShopOrder;
import org.example.kah.entity.ShopOrderAccount;
import org.example.kah.mapper.ProductAccountMapper;
import org.example.kah.mapper.ProductMapper;
import org.example.kah.mapper.ShopOrderAccountMapper;
import org.example.kah.mapper.ShopOrderMapper;
import org.example.kah.service.AdminOrderService;
import org.example.kah.service.MemberBalanceService;
import org.example.kah.service.OrderReservationService;
import org.example.kah.service.ProductCacheRefreshService;
import org.example.kah.service.ProductLockExecutorService;
import org.example.kah.service.impl.base.AbstractCrudService;
import org.example.kah.util.CryptoService;
import org.example.kah.util.CursorCodecUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminOrderServiceImpl extends AbstractCrudService<ShopOrder, Long> implements AdminOrderService {

    private static final String BALANCE_ORDER_DELETE_FORBIDDEN = "余额支付订单不允许硬删除，请使用关闭并退款";
    private static final String ENTITY_LABEL = "订单";
    private static final String ORDER_NOT_FOUND = "订单不存在";
    private static final String ORDER_CLOSE_FORBIDDEN = "仅成功订单可关闭";
    private static final String ORDER_REFUND_REMARK = "订单关闭退款";

    private final ShopOrderMapper shopOrderMapper;
    private final ShopOrderAccountMapper shopOrderAccountMapper;
    private final ProductAccountMapper productAccountMapper;
    private final ProductMapper productMapper;
    private final CryptoService cryptoService;
    private final ProductLockExecutorService productLockExecutorService;
    private final ProductCacheRefreshService productCacheRefreshService;
    private final MemberBalanceService memberBalanceService;
    private final OrderReservationService orderReservationService;

    @Override
    public CursorPageResponse<AdminOrderItemView> list(int size, String cursor, String status, Long productId, String keyword) {
        int safeSize = normalizeSize(size, 30);
        CursorCodecUtils.DecodedCursor decodedCursor = CursorCodecUtils.decode(cursor);
        Map<String, Object> params = new HashMap<>();
        params.put("status", trim(status));
        params.put("productId", productId);
        params.put("keyword", trim(keyword));
        params.put("limit", safeSize + 1);
        if (decodedCursor != null) {
            params.put("cursorCreatedAt", decodedCursor.createdAt());
            params.put("cursorId", decodedCursor.id());
        }
        List<ShopOrder> rows = shopOrderMapper.findCursorPage(params);
        boolean hasMore = rows.size() > safeSize;
        List<ShopOrder> pageItems = hasMore ? rows.subList(0, safeSize) : rows;
        String nextCursor = hasMore
                ? CursorCodecUtils.encode(pageItems.get(pageItems.size() - 1).getCreatedAt(), pageItems.get(pageItems.size() - 1).getId())
                : null;
        return new CursorPageResponse<>(pageItems.stream().map(this::toItemView).toList(), nextCursor, hasMore);
    }

    @Override
    public AdminOrderDetailView detail(Long id) {
        ShopOrder order = requireById(id);
        List<AdminOrderCardKeyView> cardKeys = shopOrderAccountMapper.findByOrderId(id).stream()
                .map(this::toCardKeyView)
                .filter(Objects::nonNull)
                .toList();
        return toDetailView(order, cardKeys);
    }

    @Override
    public AdminOrderDetailView close(Long id, String reason) {
        ShopOrder snapshot = requireById(id);
        AtomicReference<Map<String, Double>> restoredItemsRef = new AtomicReference<>(Map.of());
        return productLockExecutorService.execute(
                snapshot.getProductId(),
                () -> {
                    CloseResult result = doClose(id, reason);
                    restoredItemsRef.set(result.restoredItems());
                    return result.view();
                },
                () -> {
                    productCacheRefreshService.refreshStatsAfterWrite(snapshot.getProductId());
                    orderReservationService.addAvailableItems(snapshot.getProductId(), restoredItemsRef.get());
                });
    }

    @Override
    @Transactional
    public void delete(Long id) {
        ShopOrder order = requireById(id);
        require(!isBalanceOrder(order), ErrorCode.BAD_REQUEST, BALANCE_ORDER_DELETE_FORBIDDEN);
        shopOrderAccountMapper.deleteByOrderId(id);
        shopOrderMapper.deleteById(id);
    }

    @Override
    protected ShopOrder findEntityById(Long id) {
        return shopOrderMapper.findById(id);
    }

    @Override
    protected String entityLabel() {
        return ENTITY_LABEL;
    }

    private CloseResult doClose(Long id, String reason) {
        ShopOrder order = shopOrderMapper.lockById(id);
        if (order == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, ORDER_NOT_FOUND);
        }
        if (!OrderStatus.SUCCESS.equals(order.getStatus())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, ORDER_CLOSE_FORBIDDEN);
        }

        List<ProductAccount> accounts = productAccountMapper.lockByAssignedOrderId(id);
        int releasedCount = accounts.size();
        int restoredAvailable = (int) accounts.stream()
                .filter(account -> EnableStatus.ENABLED.equals(account.getEnableStatus()))
                .count();
        if (!accounts.isEmpty()) {
            productAccountMapper.releaseByOrderId(id);
            productMapper.adjustStats(order.getProductId(), restoredAvailable, -releasedCount);
        }

        if (isBalanceOrder(order)) {
            MemberUser memberUser = memberBalanceService.lockActiveMember(order.getUserId());
            memberBalanceService.creditForRefund(memberUser, order.getTotalAmount(), order.getOrderNo(), ORDER_REFUND_REMARK);
            shopOrderMapper.markRefunded(id);
        }

        shopOrderMapper.close(id, trim(reason));
        return new CloseResult(detail(id), toAvailableItems(accounts));
    }

    private Map<String, Double> toAvailableItems(List<ProductAccount> accounts) {
        Map<String, Double> items = new LinkedHashMap<>();
        for (ProductAccount account : accounts) {
            if (!EnableStatus.ENABLED.equals(account.getEnableStatus())) {
                continue;
            }
            if (account.getAllocationHandle() == null || account.getAllocationHandle().isBlank()) {
                continue;
            }
            items.put(account.getAllocationHandle(), account.getId().doubleValue());
        }
        return items;
    }

    private boolean isBalanceOrder(ShopOrder order) {
        return PaymentMethod.BALANCE.equals(order.getPaymentMethod());
    }

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

    private AdminOrderDetailView toDetailView(ShopOrder order, List<AdminOrderCardKeyView> cardKeys) {
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
                cardKeys);
    }

    private AdminOrderCardKeyView toCardKeyView(ShopOrderAccount account) {
        String cardKey = resolveDetailCardKey(account);
        if (cardKey == null) {
            return null;
        }
        return new AdminOrderCardKeyView(account.getAccountId(), cardKey, account.getEnableStatus(), account.getUsedStatus());
    }

    private String resolveDetailCardKey(ShopOrderAccount account) {
        if (account.getCardKeyCiphertextSnapshot() != null && !account.getCardKeyCiphertextSnapshot().isBlank()) {
            return cryptoService.decrypt(account.getCardKeyCiphertextSnapshot());
        }
        if (account.getMaskedAccountSnapshot() != null && !account.getMaskedAccountSnapshot().isBlank()) {
            return account.getMaskedAccountSnapshot();
        }
        return null;
    }

    private record CloseResult(AdminOrderDetailView view, Map<String, Double> restoredItems) {
    }
}