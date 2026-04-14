package org.example.kah.dto.admin;

import java.time.LocalDateTime;

/**
 * 后台卡密详情视图。
 * 仅补充卡密备注、更新时间和归属订单号等资源维度信息，避免与订单详情职责重复。
 */
public record AdminAccountDetailView(
        Long id,
        Long productId,
        String productTitle,
        String cardKey,
        String saleStatus,
        String enableStatus,
        String usedStatus,
        Long assignedOrderId,
        String assignedOrderNo,
        LocalDateTime assignedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        String note
) {
}
