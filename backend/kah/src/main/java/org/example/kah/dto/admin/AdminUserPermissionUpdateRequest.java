package org.example.kah.dto.admin;

import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * 管理员权限更新请求。
 */
public record AdminUserPermissionUpdateRequest(
        @NotNull(message = "权限列表不能为空")
        List<String> permissions
) {
}