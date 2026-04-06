package org.example.kah.service.impl;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.example.kah.common.BusinessException;
import org.example.kah.common.ErrorCode;
import org.example.kah.dto.publicapi.CardKeyView;
import org.example.kah.dto.publicapi.CreateOrderRequest;
import org.example.kah.dto.publicapi.OrderCreatedResponse;
import org.example.kah.dto.publicapi.OrderQueryView;
import org.example.kah.dto.publicapi.QueryOrdersRequest;
import org.example.kah.entity.EnableStatus;
import org.example.kah.entity.OrderStatus;
import org.example.kah.entity.ProductAccount;
import org.example.kah.entity.ProductStatus;
import org.example.kah.entity.ShopOrder;
import org.example.kah.entity.ShopOrderAccount;
import org.example.kah.entity.ShopProduct;
import org.example.kah.mapper.MemberUserMapper;
import org.example.kah.mapper.ProductAccountMapper;
import org.example.kah.mapper.ProductMapper;
import org.example.kah.mapper.ShopOrderAccountMapper;
import org.example.kah.mapper.ShopOrderMapper;
import org.example.kah.security.AuthenticatedUser;
import org.example.kah.service.OrderFacadeService;
import org.example.kah.service.impl.base.AbstractServiceSupport;
import org.example.kah.util.CryptoService;
import org.example.kah.util.MaskingUtils;
import org.example.kah.util.OrderNumberGenerator;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@link OrderFacadeService} 默认实现。
 * 负责前台下单事务、游客查单以及会员订单查询。
 */
@Service
@RequiredArgsConstructor
public class OrderFacadeServiceImpl extends AbstractServiceSupport implements OrderFacadeService {

    private final ProductMapper productMapper;
    private final ProductAccountMapper productAccountMapper;
    private final ShopOrderMapper shopOrderMapper;
    private final ShopOrderAccountMapper shopOrderAccountMapper;
    private final OrderNumberGenerator orderNumberGenerator;
    private final CryptoService cryptoService;
    private final MemberUserMapper memberUserMapper;

    /**
     * 创建订单并分配卡密。
     */
    @Override
    @Transactional
    public OrderCreatedResponse create(CreateOrderRequest request, AuthenticatedUser currentUser) {
        ShopProduct product = productMapper.lockById(request.productId());
        if (product == null || !ProductStatus.ACTIVE.equals(product.getStatus())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "商品不存在或已下架");
        }

        int quantity = request.quantity();
        String buyerName = trim(request.buyerName());
        String buyerContact = trim(request.buyerContact());
        String lookupHash = buildLookupHash(buyerContact, request.lookupSecret());
        if (product.getAvailableStock() < quantity) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "库存不足");
        }
        if (shopOrderMapper.countByLookupHash(lookupHash) > 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "查单密码已被占用，请重新输入");
        }

        List<ProductAccount> cardKeys = productAccountMapper.lockAvailableCardKeys(product.getId(), quantity);
        if (cardKeys.size() < quantity) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "可用卡密不足，请稍后重试");
        }

        ShopOrder order = new ShopOrder();
        order.setOrderNo(orderNumberGenerator.next());
        order.setUserId(currentUser == null ? null : currentUser.userId());
        order.setProductId(product.getId());
        order.setProductTitleSnapshot(product.getTitle() + " / " + product.getPlanName());
        order.setQuantity(quantity);
        order.setUnitPrice(product.getPrice());
        order.setTotalAmount(product.getPrice().multiply(BigDecimal.valueOf(quantity)));
        order.setBuyerName(buyerName);
        order.setBuyerContact(buyerContact);
        order.setLookupHash(lookupHash);
        order.setBuyerRemark(trim(request.remark()));
        order.setStatus(OrderStatus.SUCCESS);
        try {
            shopOrderMapper.insert(order);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "查单密码已被占用，请重新输入");
        }

        LocalDateTime now = LocalDateTime.now();
        for (ProductAccount cardKey : cardKeys) {
            productAccountMapper.assignToOrder(cardKey.getId(), order.getId(), now);
            ShopOrderAccount orderAccount = new ShopOrderAccount();
            orderAccount.setOrderId(order.getId());
            orderAccount.setAccountId(cardKey.getId());
            orderAccount.setMaskedAccountSnapshot(MaskingUtils.maskAccount(decryptCardKey(cardKey)));
            orderAccount.setCardKeyCiphertextSnapshot(cardKey.getCardKeyCiphertext());
            shopOrderAccountMapper.insert(orderAccount);
        }

        if (currentUser != null) {
            memberUserMapper.updateLastSeenAt(currentUser.userId(), now);
        }
        productMapper.syncStats();
        return new OrderCreatedResponse(
                order.getOrderNo(),
                order.getStatus(),
                quantity,
                order.getTotalAmount(),
                "下单成功，订单已创建",
                toCardKeyViews(cardKeys));
    }

    /**
     * 按联系方式和查单凭证查询订单。
     */
    @Override
    public List<OrderQueryView> queryByContact(QueryOrdersRequest request) {
        String buyerContact = trim(request.buyerContact());
        String lookupSecret = trim(request.lookupSecret());
        String orderNo = trim(request.orderNo());
        require(
                (lookupSecret != null && !lookupSecret.isBlank()) || (orderNo != null && !orderNo.isBlank()),
                "请输入联系方式和查单密码，或使用旧订单号兼容查询");

        Map<String, Object> params = new HashMap<>();
        params.put("buyerContact", buyerContact);
        if (lookupSecret != null && !lookupSecret.isBlank()) {
            params.put("lookupHash", buildLookupHash(buyerContact, lookupSecret));
        } else {
            params.put("orderNo", orderNo);
        }
        return shopOrderMapper.findByContact(params).stream().map(this::toView).toList();
    }

    /**
     * 查询会员已绑定订单，并刷新上次活跃时间。
     */
    @Override
    public List<OrderQueryView> listByUser(Long userId) {
        memberUserMapper.updateLastSeenAt(userId, LocalDateTime.now());
        return shopOrderMapper.findByUserId(userId).stream().map(this::toView).toList();
    }

    /**
     * 将订单实体映射为前台查询视图。
     */
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

    /**
     * 生成订单卡密列表视图。
     */
    private List<CardKeyView> loadCardKeys(Long orderId) {
        return shopOrderAccountMapper.findByOrderId(orderId).stream()
                .filter(item -> item.getCardKeyCiphertextSnapshot() != null && !item.getCardKeyCiphertextSnapshot().isBlank())
                .map(item -> new CardKeyView(
                        cryptoService.decrypt(item.getCardKeyCiphertextSnapshot()),
                        item.getEnableStatus() == null || item.getEnableStatus().isBlank() ? EnableStatus.DISABLED : item.getEnableStatus()))
                .toList();
    }

    /**
     * 将分配到的资源实体转换为返回视图。
     */
    private List<CardKeyView> toCardKeyViews(List<ProductAccount> cardKeys) {
        return cardKeys.stream()
                .map(item -> new CardKeyView(decryptCardKey(item), item.getEnableStatus()))
                .toList();
    }

    /**
     * 解密卡密正文。
     */
    private String decryptCardKey(ProductAccount account) {
        return cryptoService.decrypt(account.getCardKeyCiphertext());
    }

    /**
     * 根据联系方式与查单密码生成唯一查单哈希。
     */
    private String buildLookupHash(String buyerContact, String lookupSecret) {
        return cryptoService.digest(buyerContact + ":" + trim(lookupSecret));
    }
}