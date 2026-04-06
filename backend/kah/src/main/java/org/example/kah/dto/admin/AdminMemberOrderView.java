package org.example.kah.dto.admin;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 会员详情中展示的订单快照视图。
 */
public record AdminMemberOrderView(
        Long id,
        String orderNo,
        Long productId,
        String productTitle,
        Integer quantity,
        BigDecimal totalAmount,
        String buyerContact,
        String status,
        LocalDateTime createdAt,
        List<String> cardKeys
) {
}