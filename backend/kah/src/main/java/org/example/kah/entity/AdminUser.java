package org.example.kah.entity;

import java.time.LocalDateTime;
import lombok.Data;

/**
 * 后台管理员实体。
 * 对应表 {@code admin_user}，保存管理员登录账号、显示名、状态与最高权限标记。
 */
@Data
public class AdminUser {

    /** 管理员主键。 */
    private Long id;

    /** 后台登录用户名。 */
    private String username;

    /** BCrypt 密码摘要。 */
    private String passwordHash;

    /** 管理员状态，例如 ACTIVE / DISABLED。 */
    private String status;

    /** 后台展示名称。 */
    private String displayName;

    /** 是否为最高权限管理员。 */
    private Boolean isSuperAdmin;

    /** 最近登录时间。 */
    private LocalDateTime lastLoginAt;

    /** 创建时间。 */
    private LocalDateTime createdAt;

    /** 更新时间。 */
    private LocalDateTime updatedAt;
}