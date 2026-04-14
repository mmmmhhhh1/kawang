package org.example.kah.dto.admin;

/**
 * 后台订单详情中的卡密明细视图。
 * 除卡密正文外，额外返回启用状态和使用状态，便于管理端排查发货后的卡密状态。
 */
public record AdminOrderCardKeyView(
        Long accountId,
        String cardKey,
        String enableStatus,
        String usedStatus
) {
}
