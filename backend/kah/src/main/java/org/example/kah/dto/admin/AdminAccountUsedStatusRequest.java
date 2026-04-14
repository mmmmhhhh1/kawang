package org.example.kah.dto.admin;

import jakarta.validation.constraints.NotBlank;

/**
 * 卡密使用状态更新请求。
 * 用于在后台标记某张已售卡密是否已经被实际使用。
 */
public record AdminAccountUsedStatusRequest(
        @NotBlank(message = "卡密使用状态不能为空")
        String usedStatus
) {
}
