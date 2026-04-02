package org.example.kah.dto.admin;

import jakarta.validation.constraints.NotBlank;

/**
 * 后台批量停用卡密请求。
 * scope 为 PRODUCT 时必须额外传入 productId。
 */
public record AdminAccountBulkDisableRequest(
        @NotBlank(message = "停用范围不能为空")
        String scope,
        Long productId
) {
}