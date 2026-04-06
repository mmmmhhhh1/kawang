package org.example.kah.entity;

import java.time.LocalDateTime;
import lombok.Data;

/**
 * 前台会员实体。
 * 对应表 {@code member_user}，保存会员用户名、邮箱、密码摘要和最近活跃信息。
 */
@Data
public class MemberUser {

    /** 会员主键。 */
    private Long id;

    /** 会员邮箱，允许为空以兼容旧的用户名注册用户。 */
    private String mail;

    /** 会员用户名。 */
    private String username;

    /** BCrypt 密码摘要。 */
    private String passwordHash;

    /** 会员状态，例如 ACTIVE / DISABLED。 */
    private String status;

    /** 最近登录时间。 */
    private LocalDateTime lastLoginAt;

    /** 最近活跃时间。 */
    private LocalDateTime lastSeenAt;

    /** 创建时间。 */
    private LocalDateTime createdAt;

    /** 更新时间。 */
    private LocalDateTime updatedAt;
}