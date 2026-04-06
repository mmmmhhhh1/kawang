package org.example.kah.dto.admin;

import java.util.List;

/**
 * 后台当前管理员资料响应。
 * 前端使用该结构决定菜单可见性与操作按钮权限。
 */
public record AdminProfileResponse(
        Long id,
        String username,
        String displayName,
        boolean isSuperAdmin,
        List<String> permissions
) {
}