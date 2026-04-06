package org.example.kah.dto.admin;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 管理员列表项视图。
 */
public record AdminUserItemView(
        Long id,
        String username,
        String displayName,
        boolean isSuperAdmin,
        List<String> permissions,
        LocalDateTime createdAt
) {
}