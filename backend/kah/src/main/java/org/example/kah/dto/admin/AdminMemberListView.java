package org.example.kah.dto.admin;

import java.time.LocalDateTime;

/**
 * 后台会员基础列表视图。
 */
public record AdminMemberListView(
        Long id,
        String username,
        String email,
        String status,
        LocalDateTime createdAt
) {
}