package org.example.kah.dto.admin;

import java.time.LocalDateTime;

/**
 * 卡密详情中的订单摘要视图。
 * 仅返回管理端查看一条已售卡密对应订单所需的关键信息。
 */
public record AdminAccountOrderInfoView(
        Long id,
        String orderNo,
        String buyerName,
        String buyerContact,
        LocalDateTime createdAt,
        String status
) {
}
