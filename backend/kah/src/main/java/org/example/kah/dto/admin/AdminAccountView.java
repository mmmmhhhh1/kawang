package org.example.kah.dto.admin;

import java.time.LocalDateTime;

/**
 * 后台卡密池列表视图。
 * 管理端展示卡密正文、销售状态、启用状态、使用状态以及购买订单号。
 */
public record AdminAccountView(
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
        LocalDateTime createdAt
) {
}
