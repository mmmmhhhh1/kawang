package org.example.kah.dto.admin;

import java.time.LocalDateTime;

/**
 * 后台会员列表项视图。
 */
public record AdminMemberListView(
        Long id,
        String username,
        String email,
        String status,
        LocalDateTime lastSeenAt,
        LocalDateTime lastLoginAt,
        LocalDateTime createdAt
) {
}