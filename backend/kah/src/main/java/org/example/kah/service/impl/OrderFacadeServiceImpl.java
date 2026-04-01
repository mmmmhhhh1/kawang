package org.example.kah.service.impl;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.example.kah.common.BusinessException;
import org.example.kah.common.ErrorCode;
import org.example.kah.dto.publicapi.CreateOrderRequest;
import org.example.kah.dto.publicapi.OrderCreatedResponse;
import org.example.kah.dto.publicapi.OrderQueryView;
import org.example.kah.dto.publicapi.QueryOrdersRequest;
import org.example.kah.entity.OrderStatus;
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
import org.example.kah.service.OrderFacadeService;
import org.example.kah.service.impl.base.AbstractServiceSupport;
import org.example.kah.util.OrderNumberGenerator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@link OrderFacadeService} 的默认实现。
 * 负责前台下单事务、联系方式查单以及会员订单列表查询。
 */
@Service
@RequiredArgsConstructor
public class OrderFacadeServiceImpl extends AbstractServiceSupport implements OrderFacadeService {

    private final ProductMapper productMapper;
    private final ProductAccountMapper productAccountMapper;
    private final ShopOrderMapper shopOrderMapper;
    private final ShopOrderAccountMapper shopOrderAccountMapper;
    private final OrderNumberGenerator orderNumberGenerator;

    /**
     * 创建订单。
     * 该流程会同时锁定商品记录与足量可用账号，防止并发超卖。
     */
    @Override
    @Transactional
    public OrderCreatedResponse create(CreateOrderRequest request, AuthenticatedUser currentUser) {
        ShopProduct product = productMapper.lockById(request.productId());
        if (product == null || !ProductStatus.ACTIVE.equals(product.getStatus())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "商品不存在或已下架");
        }

        int quantity = request.quantity();
        if (product.getAvailableStock() < quantity) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "库存不足");
        }

        List<ProductAccount> accounts = productAccountMapper.lockAvailableAccounts(product.getId(), quantity);
        if (accounts.size() < quantity) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "可用账号不足，请稍后重试");
        }

        ShopOrder order = new ShopOrder();
        order.setOrderNo(orderNumberGenerator.next());
        order.setUserId(currentUser == null ? null : currentUser.userId());
        order.setProductId(product.getId());
        order.setProductTitleSnapshot(product.getTitle() + " · " + product.getPlanName());
        order.setQuantity(quantity);
        order.setUnitPrice(product.getPrice());
        order.setTotalAmount(product.getPrice().multiply(BigDecimal.valueOf(quantity)));
        order.setBuyerName(trim(request.buyerName()));
        order.setBuyerContact(trim(request.buyerContact()));
        order.setBuyerRemark(trim(request.remark()));
        order.setStatus(OrderStatus.SUCCESS);
        shopOrderMapper.insert(order);

        LocalDateTime assignedAt = LocalDateTime.now();
        for (ProductAccount account : accounts) {
            productAccountMapper.assignToOrder(account.getId(), order.getId(), assignedAt);
            ShopOrderAccount orderAccount = new ShopOrderAccount();
            orderAccount.setOrderId(order.getId());
            orderAccount.setAccountId(account.getId());
            orderAccount.setMaskedAccountSnapshot(account.getAccountNameMasked());
            shopOrderAccountMapper.insert(orderAccount);
        }

        productMapper.syncStats();
        return new OrderCreatedResponse(order.getOrderNo(), order.getStatus(), quantity, order.getTotalAmount(), "下单成功，订单已创建");
    }

    /**
     * 按联系方式和可选订单号查询订单。
     */
    @Override
    public List<OrderQueryView> queryByContact(QueryOrdersRequest request) {
        Map<String, Object> params = new HashMap<>();
        params.put("buyerContact", trim(request.buyerContact()));
        params.put("orderNo", trim(request.orderNo()));
        return shopOrderMapper.findByContact(params).stream().map(this::toView).toList();
    }

    /**
     * 查询会员已绑定订单。
     */
    @Override
    public List<OrderQueryView> listByUser(Long userId) {
        return shopOrderMapper.findByUserId(userId).stream().map(this::toView).toList();
    }

    /**
     * 将订单实体映射为前台查单视图。
     */
    private OrderQueryView toView(ShopOrder order) {
        return new OrderQueryView(
                order.getId(),
                order.getOrderNo(),
                order.getProductTitleSnapshot(),
                order.getQuantity(),
                order.getTotalAmount(),
                order.getStatus(),
                order.getCreatedAt());
    }
}
