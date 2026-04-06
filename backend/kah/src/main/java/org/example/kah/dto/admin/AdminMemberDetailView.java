package org.example.kah.dto.admin;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 后台会员详情视图。
 */
public record AdminMemberDetailView(
        Long id,
        String username,
        String email,
        String status,
        LocalDateTime lastSeenAt,
        LocalDateTime lastLoginAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        List<AdminMemberOrderView> orders
) {
}