package org.example.kah.dto.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 后台单条卡密导入项。
 * 新版资源池只要求卡密字符串和可选备注。
 */
public record AdminAccountItemRequest(
        @NotBlank(message = "卡密不能为空")
        @Size(max = 255, message = "卡密长度不能超过 255")
        String cardKey,
        @Size(max = 200, message = "备注长度不能超过 200")
        String note
) {
}