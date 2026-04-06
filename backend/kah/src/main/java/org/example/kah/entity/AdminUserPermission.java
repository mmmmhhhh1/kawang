package org.example.kah.entity;

import java.time.LocalDateTime;
import lombok.Data;

/**
 * 管理员权限关系实体。
 * 对应表 {@code admin_user_permission}，用于保存普通管理员被授予的权限编码。
 */
@Data
public class AdminUserPermission {

    /** 关系主键。 */
    private Long id;

    /** 管理员主键。 */
    private Long adminUserId;

    /** 权限编码。 */
    private String permissionCode;

    /** 创建时间。 */
    private LocalDateTime createdAt;
}