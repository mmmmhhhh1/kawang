package org.example.kah.entity;

import java.time.LocalDateTime;
import lombok.Data;

/**
 * 前台会员实体。
 * 对应表 {@code member_user}，用于保存前台注册账号及登录状态信息。
 */
@Data
public class MemberUser {

    /** 会员主键。 */
    private Long id;

    /** 会员邮箱*/
    private String mail;
    /** 会员用户名。 */
    private String username;

    /** BCrypt 密码摘要。 */
    private String passwordHash;

    /** 会员状态，例如 ACTIVE / DISABLED。 */
    private String status;

    /** 最后登录时间。 */
    private LocalDateTime lastLoginAt;

    /** 创建时间。 */
    private LocalDateTime createdAt;

    /** 最后更新时间。 */
    private LocalDateTime updatedAt;

}
