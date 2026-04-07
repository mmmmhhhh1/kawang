package org.example.kah.dto.admin;

import java.time.LocalDateTime;

/**
 * 后台会员活动信息视图。
 */
public record AdminMemberActivityView(
        Long userId,
        LocalDateTime lastSeenAt,
        LocalDateTime lastLoginAt
) {
}