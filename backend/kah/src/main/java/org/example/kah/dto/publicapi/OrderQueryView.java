package org.example.kah.dto.publicapi;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 前台订单查询视图。
 * 查单结果会返回订单信息以及该订单对应的卡密列表。
 */
public record OrderQueryView(
        Long id,
        String orderNo,
        String productTitle,
        Integer quantity,
        BigDecimal totalAmount,
        String status,
        LocalDateTime createdAt,
        List<CardKeyView> cardKeys
) {
}