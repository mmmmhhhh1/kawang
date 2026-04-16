package org.example.kah.service.impl;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.example.kah.annotation.TrackMemberSeen;
import org.example.kah.common.BusinessException;
import org.example.kah.common.ErrorCode;
import org.example.kah.dto.publicapi.CardKeyView;
import org.example.kah.dto.publicapi.CreateOrderRequest;
import org.example.kah.dto.publicapi.OrderCreatedResponse;
import org.example.kah.dto.publicapi.OrderQueryView;
import org.example.kah.dto.publicapi.QueryOrdersRequest;
import org.example.kah.entity.EnableStatus;
import org.example.kah.entity.MemberUser;
import org.example.kah.entity.OrderStatus;
import org.example.kah.entity.PaymentMethod;
import org.example.kah.entity.ProductAccount;
import org.example.kah.entity.ProductStatus;
import org.example.kah.entity.ShopOrder;
import org.example.kah.entity.ShopOrderAccount;
import org.example.kah.entity.ShopProduct;
import org.example.kah.mapper.ProductAccountMapper;
import org.example.kah.mapper.ProductMapper;
import org.example.kah.mapper.ShopOrderAccountMapper;
import org.example.kah.mapper.ShopOrderMapper;
import org.example.kah.reservation.OrderReservation;
import org.example.kah.security.AuthenticatedUser;
import org.example.kah.service.MemberBalanceService;
import org.example.kah.service.OrderFacadeService;
import org.example.kah.service.OrderReservationService;
import org.example.kah.service.ProductCacheRefreshService;
import org.example.kah.service.impl.base.AbstractServiceSupport;
import org.example.kah.util.CryptoService;
import org.example.kah.util.MaskingUtils;
import org.example.kah.util.OrderNumberGenerator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@RequiredArgsConstructor
public class OrderFacadeServiceImpl extends AbstractServiceSupport implements OrderFacadeService {

    private static final String LOGIN_REQUIRED = "请先登录后再购买";
    private static final String LOOKUP_REQUIRED = "请输入联系方式和查单密码，或使用旧订单号兼容查询";
    private static final String PRODUCT_NOT_FOUND = "商品不存在或已下架";
    private static final String INVALID_QUANTITY = "购买数量至少为 1";
    private static final String BALANCE_ORDER_REMARK = "余额购买卡密";
    private static final String ORDER_SUCCESS_MESSAGE = "下单成功，卡密已发放";
    private static final String ORDER_ASSIGN_FAILED = "卡密分配状态异常，请稍后重试";

    private final ProductMapper productMapper;
    private final ProductAccountMapper productAccountMapper;
    private final ShopOrderMapper shopOrderMapper;
    private final ShopOrderAccountMapper shopOrderAccountMapper;
    private final OrderNumberGenerator orderNumberGenerator;
    private final CryptoService cryptoService;
    private final TransactionTemplate transactionTemplate;
    private final ProductCacheRefreshService productCacheRefreshService;
    private final MemberBalanceService memberBalanceService;
    private final OrderReservationService orderReservationService;

    @Override
    @TrackMemberSeen
    public OrderCreatedResponse create(CreateOrderRequest request, AuthenticatedUser currentUser) {
        require(currentUser != null, ErrorCode.UNAUTHORIZED, LOGIN_REQUIRED);
        int quantity = request.quantity() == null ? 0 : request.quantity();
        if (quantity < 1) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, INVALID_QUANTITY);
        }

        ShopProduct snapshot = productMapper.findById(request.productId());
        if (snapshot == null || !ProductStatus.ACTIVE.equals(snapshot.getStatus())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, PRODUCT_NOT_FOUND);
        }

        OrderReservation reservation = orderReservationService.reserve(request.productId(), currentUser.userId(), quantity);
        try {
            OrderCreatedResponse response = transactionTemplate.execute(status -> doCreate(request, currentUser, reservation));
            orderReservationService.confirm(reservation);
            productCacheRefreshService.refreshStatsAfterWrite(request.productId());
            return response;
        } catch (RuntimeException | Error exception) {
            orderReservationService.rollback(reservation);
            throw exception;
        }
    }

    @Override
    public List<OrderQueryView> queryByContact(QueryOrdersRequest request) {
        String buyerContact = trim(request.buyerContact());
        String lookupSecret = trim(request.lookupSecret());
        String orderNo = trim(request.orderNo());
        require(
                (lookupSecret != null && !lookupSecret.isBlank()) || (orderNo != null && !orderNo.isBlank()),
                LOOKUP_REQUIRED);

        Map<String, Object> params = new HashMap<>();
        params.put("buyerContact", buyerContact);
        if (lookupSecret != null && !lookupSecret.isBlank()) {
            params.put("lookupHash", buildLookupHash(buyerContact, lookupSecret));
        } else {
            params.put("orderNo", orderNo);
        }
        return shopOrderMapper.findByContact(params).stream().map(this::toView).toList();
    }

    @Override
    @TrackMemberSeen
    public List<OrderQueryView> listByUser(Long userId) {
        return shopOrderMapper.findByUserId(userId).stream().map(this::toView).toList();
    }

    private OrderCreatedResponse doCreate(CreateOrderRequest request, AuthenticatedUser currentUser, OrderReservation reservation) {
        ShopProduct product = productMapper.findById(request.productId());
        if (product == null || !ProductStatus.ACTIVE.equals(product.getStatus())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, PRODUCT_NOT_FOUND);
        }

        int quantity = reservation.items().size();
        if (quantity < 1) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, INVALID_QUANTITY);
        }

        BigDecimal totalAmount = product.getPrice().multiply(BigDecimal.valueOf(quantity));
        String orderNo = orderNumberGenerator.next();
        MemberUser memberUser = memberBalanceService.debitForOrder(currentUser.userId(), totalAmount, orderNo, BALANCE_ORDER_REMARK);

        ShopOrder order = new ShopOrder();
        order.setOrderNo(orderNo);
        order.setUserId(memberUser.getId());
        order.setProductId(product.getId());
        order.setProductTitleSnapshot(product.getTitle() + " / " + product.getPlanName());
        order.setQuantity(quantity);
        order.setUnitPrice(product.getPrice());
        order.setTotalAmount(totalAmount);
        order.setPaymentMethod(PaymentMethod.BALANCE);
        order.setBalanceAmount(totalAmount);
        order.setBuyerName(memberUser.getUsername());
        order.setBuyerContact(memberUser.getMail() == null || memberUser.getMail().isBlank() ? memberUser.getUsername() : memberUser.getMail());
        order.setLookupHash(null);
        order.setBuyerRemark(trim(request.remark()));
        order.setStatus(OrderStatus.SUCCESS);
        shopOrderMapper.insert(order);

        List<String> handles = reservation.items().stream().map(item -> item.handle()).toList();
        LocalDateTime now = LocalDateTime.now();
        int updated = productAccountMapper.assignBatchToOrderByHandles(product.getId(), handles, order.getId(), now);
        if (updated != handles.size()) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, ORDER_ASSIGN_FAILED);
        }

        List<ProductAccount> cardKeys = loadAssignedCardKeys(product.getId(), handles, order.getId());
        List<ShopOrderAccount> snapshots = cardKeys.stream().map(cardKey -> toSnapshot(order.getId(), cardKey)).toList();
        if (!snapshots.isEmpty()) {
            shopOrderAccountMapper.batchInsert(snapshots);
        }

        productMapper.adjustStats(product.getId(), -quantity, quantity);
        return new OrderCreatedResponse(
                order.getOrderNo(),
                order.getStatus(),
                quantity,
                order.getTotalAmount(),
                ORDER_SUCCESS_MESSAGE,
                toCardKeyViews(cardKeys),
                memberUser.getBalance());
    }

    private List<ProductAccount> loadAssignedCardKeys(Long productId, List<String> handles, Long orderId) {
        Map<String, ProductAccount> accountMap = productAccountMapper.findByAllocationHandles(productId, handles).stream()
                .collect(LinkedHashMap::new, (map, account) -> map.put(account.getAllocationHandle(), account), Map::putAll);
        List<ProductAccount> orderedAccounts = new java.util.ArrayList<>();
        for (String handle : handles) {
            ProductAccount account = accountMap.get(handle);
            if (account == null || account.getAssignedOrderId() == null || !orderId.equals(account.getAssignedOrderId())) {
                throw new BusinessException(ErrorCode.INTERNAL_ERROR, ORDER_ASSIGN_FAILED);
            }
            orderedAccounts.add(account);
        }
        return orderedAccounts;
    }

    private ShopOrderAccount toSnapshot(Long orderId, ProductAccount cardKey) {
        ShopOrderAccount snapshot = new ShopOrderAccount();
        snapshot.setOrderId(orderId);
        snapshot.setAccountId(cardKey.getId());
        snapshot.setMaskedAccountSnapshot(MaskingUtils.maskAccount(decryptCardKey(cardKey)));
        snapshot.setCardKeyCiphertextSnapshot(cardKey.getCardKeyCiphertext());
        return snapshot;
    }

    private OrderQueryView toView(ShopOrder order) {
        return new OrderQueryView(
                order.getId(),
                order.getOrderNo(),
                order.getProductTitleSnapshot(),
                order.getQuantity(),
                order.getTotalAmount(),
                order.getStatus(),
                order.getCreatedAt(),
                loadCardKeys(order.getId()));
    }

    private List<CardKeyView> loadCardKeys(Long orderId) {
        return shopOrderAccountMapper.findByOrderId(orderId).stream()
                .filter(item -> item.getCardKeyCiphertextSnapshot() != null && !item.getCardKeyCiphertextSnapshot().isBlank())
                .map(item -> new CardKeyView(
                        cryptoService.decrypt(item.getCardKeyCiphertextSnapshot()),
                        item.getEnableStatus() == null || item.getEnableStatus().isBlank()
                                ? EnableStatus.DISABLED
                                : item.getEnableStatus()))
                .toList();
    }

    private List<CardKeyView> toCardKeyViews(List<ProductAccount> cardKeys) {
        return cardKeys.stream()
                .map(item -> new CardKeyView(decryptCardKey(item), item.getEnableStatus()))
                .toList();
    }

    private String decryptCardKey(ProductAccount account) {
        return cryptoService.decrypt(account.getCardKeyCiphertext());
    }

    private String buildLookupHash(String buyerContact, String lookupSecret) {
        return cryptoService.digest(buyerContact + ":" + trim(lookupSecret));
    }
}
