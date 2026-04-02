package org.example.kah.dto.admin;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 后台订单详情视图。
 * 详情页保留订单基础字段，并展示该订单分配到的卡密列表。
 */
public record AdminOrderDetailView(
        Long id,
        String orderNo,
        Long productId,
        String productTitleSnapshot,
        Integer quantity,
        BigDecimal unitPrice,
        BigDecimal totalAmount,
        String buyerName,
        String buyerContact,
        String buyerRemark,
        String status,
        String closedReason,
        LocalDateTime createdAt,
        List<String> cardKeys
) {
}