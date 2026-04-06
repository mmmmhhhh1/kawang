package org.example.kah.dto.admin;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 管理员详情视图。
 */
public record AdminUserDetailView(
        Long id,
        String username,
        String displayName,
        boolean isSuperAdmin,
        List<String> permissions,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}