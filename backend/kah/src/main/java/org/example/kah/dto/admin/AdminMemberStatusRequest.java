package org.example.kah.dto.admin;

import jakarta.validation.constraints.NotBlank;

/**
 * 会员状态切换请求。
 */
public record AdminMemberStatusRequest(
        @NotBlank(message = "状态不能为空")
        String status
) {
}