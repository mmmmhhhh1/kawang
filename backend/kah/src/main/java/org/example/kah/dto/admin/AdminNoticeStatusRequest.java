package org.example.kah.dto.admin;

import jakarta.validation.constraints.NotBlank;

public record AdminNoticeStatusRequest(
        @NotBlank(message = "公告状态不能为空")
        String status
) {
}
