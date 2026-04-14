package org.example.kah.service.impl;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
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
import org.example.kah.security.AuthenticatedUser;
import org.example.kah.service.MemberBalanceService;
import org.example.kah.service.OrderFacadeService;
import org.example.kah.service.ProductCacheRefreshService;
import org.example.kah.service.ProductLockExecutorService;
import org.example.kah.service.impl.base.AbstractServiceSupport;
import org.example.kah.util.CryptoService;
import org.example.kah.util.MaskingUtils;
import org.example.kah.util.OrderNumberGenerator;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class OrderFacadeServiceImpl extends AbstractServiceSupport implements OrderFacadeService {

    private static final String LOGIN_REQUIRED = "\u8bf7\u5148\u767b\u5f55\u540e\u518d\u8d2d\u4e70";
    private static final String LOOKUP_REQUIRED = "\u8bf7\u8f93\u5165\u8054\u7cfb\u65b9\u5f0f\u548c\u67e5\u5355\u5bc6\u7801\uff0c\u6216\u4f7f\u7528\u65e7\u8ba2\u5355\u53f7\u517c\u5bb9\u67e5\u8be2";
    private static final String PRODUCT_NOT_FOUND = "\u5546\u54c1\u4e0d\u5b58\u5728\u6216\u5df2\u4e0b\u67b6";
    private static final String INVALID_QUANTITY = "\u8d2d\u4e70\u6570\u91cf\u81f3\u5c11\u4e3a 1";
    private static final String INSUFFICIENT_CARD_KEYS = "\u53ef\u7528\u5361\u5bc6\u4e0d\u8db3\uff0c\u8bf7\u7a0d\u540e\u518d\u8bd5";
    private static final String BALANCE_ORDER_REMARK = "\u4f59\u989d\u8d2d\u4e70\u5361\u5bc6";
    private static final String ORDER_SUCCESS_MESSAGE = "\u4e0b\u5355\u6210\u529f\uff0c\u5361\u5bc6\u5df2\u53d1\u653e";

    private final ProductMapper productMapper;
    private final ProductAccountMapper productAccountMapper;
    private final ShopOrderMapper shopOrderMapper;
    private final ShopOrderAccountMapper shopOrderAccountMapper;
    private final OrderNumberGenerator orderNumberGenerator;
    private final CryptoService cryptoService;
    private final ProductLockExecutorService productLockExecutorService;
    private final ProductCacheRefreshService productCacheRefreshService;
    private final MemberBalanceService memberBalanceService;

    @Override
    @TrackMemberSeen
    public OrderCreatedResponse create(CreateOrderRequest request, AuthenticatedUser currentUser) {
        require(currentUser != null, ErrorCode.UNAUTHORIZED, LOGIN_REQUIRED);
        return productLockExecutorService.execute(
                request.productId(),
                () -> doCreate(request, currentUser),
                () -> productCacheRefreshService.refreshStatsAfterWrite(request.productId()));
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

    private OrderCreatedResponse doCreate(CreateOrderRequest request, AuthenticatedUser currentUser) {
        MemberUser memberUser = memberBalanceService.lockActiveMember(currentUser.userId());
        ShopProduct product = productMapper.findById(request.productId());
        if (product == null || !ProductStatus.ACTIVE.equals(product.getStatus())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, PRODUCT_NOT_FOUND);
        }

        int quantity = request.quantity();
        if (quantity < 1) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, INVALID_QUANTITY);
        }

        List<ProductAccount> cardKeys = productAccountMapper.lockAvailableCardKeys(product.getId(), quantity);
        if (cardKeys.size() < quantity) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, INSUFFICIENT_CARD_KEYS);
        }

        BigDecimal totalAmount = product.getPrice().multiply(BigDecimal.valueOf(quantity));
        String orderNo = orderNumberGenerator.next();
        memberBalanceService.debitForOrder(memberUser, totalAmount, orderNo, BALANCE_ORDER_REMARK);

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

        LocalDateTime now = LocalDateTime.now();
        List<Long> cardKeyIds = cardKeys.stream().map(ProductAccount::getId).toList();
        productAccountMapper.assignBatchToOrder(cardKeyIds, order.getId(), now);

        List<ShopOrderAccount> snapshots = cardKeys.stream()
                .map(cardKey -> toSnapshot(order.getId(), cardKey))
                .toList();
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