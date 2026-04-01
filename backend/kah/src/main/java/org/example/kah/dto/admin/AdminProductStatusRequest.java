package org.example.kah.dto.admin;

import jakarta.validation.constraints.NotBlank;

public record AdminProductStatusRequest(
        @NotBlank(message = "状态不能为空")
        String status
) {
}
