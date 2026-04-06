package org.example.kah.dto.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * 新建管理员请求。
 */
public record AdminUserCreateRequest(
        @NotBlank(message = "用户名不能为空")
        @Size(min = 4, max = 32, message = "用户名长度需在 4 到 32 之间")
        @Pattern(regexp = "^[a-zA-Z0-9_]+$", message = "用户名只能包含字母、数字和下划线")
        String username,

        @NotBlank(message = "显示名不能为空")
        @Size(min = 2, max = 32, message = "显示名长度需在 2 到 32 之间")
        String displayName,

        @NotBlank(message = "初始密码不能为空")
        @Size(min = 6, max = 64, message = "初始密码长度需在 6 到 64 之间")
        String password,

        @NotNull(message = "权限列表不能为空")
        List<String> permissions
) {
}