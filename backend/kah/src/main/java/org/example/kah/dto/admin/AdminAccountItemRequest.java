package org.example.kah.dto.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AdminAccountItemRequest(
        @NotBlank(message = "账号不能为空")
        @Size(max = 120, message = "账号长度不能超过 120")
        String accountName,
        @NotBlank(message = "密码不能为空")
        @Size(max = 120, message = "密码长度不能超过 120")
        String secret,
        @Size(max = 200, message = "备注长度不能超过 200")
        String note
) {
}
