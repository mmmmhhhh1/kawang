package org.example.kah.dto.admin;

import java.time.LocalDateTime;

/**
 * 后台卡密池视图。
 * 管理端展示卡密正文、售卖状态和启用状态。
 */
public record AdminAccountView(
        Long id,
        Long productId,
        String productTitle,
        String cardKey,
        String saleStatus,
        String enableStatus,
        Long assignedOrderId,
        LocalDateTime assignedAt,
        LocalDateTime createdAt
) {
}