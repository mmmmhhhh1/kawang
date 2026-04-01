package org.example.kah.service;

import org.example.kah.dto.admin.AdminLoginResponse;
import org.example.kah.dto.admin.AdminProfileResponse;
import org.example.kah.security.AuthenticatedUser;

/**
 * 后台管理员认证服务接口。
 * 负责管理员登录和当前登录管理员信息读取。
 */
public interface AdminAuthService {

    /**
     * 管理员登录并返回 JWT。
     *
     * @param username 用户名
     * @param password 明文密码
     * @return 登录结果
     */
    AdminLoginResponse login(String username, String password);

    /**
     * 读取当前登录管理员信息。
     *
     * @param currentUser 当前认证主体
     * @return 管理员资料
     */
    AdminProfileResponse me(AuthenticatedUser currentUser);
}
