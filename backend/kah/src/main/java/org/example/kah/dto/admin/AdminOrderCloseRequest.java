package org.example.kah.dto.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AdminOrderCloseRequest(
        @NotBlank(message = "关闭原因不能为空")
        @Size(max = 120, message = "关闭原因长度不能超过 120")
        String reason
) {
}
