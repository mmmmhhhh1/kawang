package org.example.kah.dto.publicapi;

/**
 * 前台卡密展示视图。
 * 仅在订单已授权场景下返回卡密正文和当前启用状态。
 */
public record CardKeyView(
        String cardKey,
        String enableStatus
) {
}