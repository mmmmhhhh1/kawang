package org.example.kah.dto.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AdminNoticeSaveRequest(
        @NotBlank(message = "公告标题不能为空")
        @Size(max = 120, message = "公告标题长度不能超过 120")
        String title,
        @NotBlank(message = "公告摘要不能为空")
        @Size(max = 255, message = "公告摘要长度不能超过 255")
        String summary,
        @NotBlank(message = "公告内容不能为空")
        String content,
        @NotBlank(message = "公告状态不能为空")
        String status,
        @NotNull(message = "排序不能为空")
        Integer sortOrder
) {
}
