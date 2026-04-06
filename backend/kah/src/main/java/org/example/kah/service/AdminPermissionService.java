package org.example.kah.service;

import org.example.kah.dto.admin.AdminProfileResponse;
import org.example.kah.entity.AdminUser;
import org.example.kah.security.AuthenticatedUser;

/**
 * 后台管理员权限服务接口。
 * 负责构建管理员资料响应，并对受限操作执行权限校验。
 */
public interface AdminPermissionService {

    /** 将管理员实体转换为带权限信息的资料响应。 */
    AdminProfileResponse buildProfile(AdminUser adminUser);

    /** 对当前登录管理员执行权限校验。 */
    void requirePermission(AuthenticatedUser currentUser, String permission);
}