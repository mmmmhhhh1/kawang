package org.example.kah.dto.publicapi;

import java.math.BigDecimal;
import java.util.List;

/**
 * 下单成功响应。
 * 除了基础订单信息，还会返回本次实际分配到的卡密列表。
 */
public record OrderCreatedResponse(
        String orderNo,
        String status,
        Integer quantity,
        BigDecimal totalAmount,
        String message,
        List<CardKeyView> cardKeys
) {
}