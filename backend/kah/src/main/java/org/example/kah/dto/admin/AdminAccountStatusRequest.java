package org.example.kah.dto.admin;

import jakarta.validation.constraints.NotBlank;

/**
 * 后台卡密启用状态变更请求。
 */
public record AdminAccountStatusRequest(
        @NotBlank(message = "启用状态不能为空")
        String enableStatus
) {
}