package org.example.kah.service.impl;

import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.example.kah.common.BusinessException;
import org.example.kah.common.ErrorCode;
import org.example.kah.dto.admin.AdminLoginResponse;
import org.example.kah.dto.admin.AdminProfileResponse;
import org.example.kah.entity.AdminStatus;
import org.example.kah.entity.AdminUser;
import org.example.kah.mapper.AdminUserMapper;
import org.example.kah.security.AuthenticatedUser;
import org.example.kah.security.JwtService;
import org.example.kah.service.AdminAuthService;
import org.example.kah.service.AdminPermissionService;
import org.example.kah.service.impl.base.AbstractServiceSupport;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * {@link AdminAuthService} 默认实现。
 * 负责管理员登录鉴权与当前登录管理员资料读取。
 */
@Service
@RequiredArgsConstructor
public class AdminAuthServiceImpl extends AbstractServiceSupport implements AdminAuthService {

    private final AdminUserMapper adminUserMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AdminPermissionService adminPermissionService;

    /**
     * 校验管理员账号密码并签发后台 JWT。
     */
    @Override
    public AdminLoginResponse login(String username, String password) {
        AdminUser adminUser = adminUserMapper.findByUsername(trim(username));
        if (adminUser == null || !passwordEncoder.matches(password, adminUser.getPasswordHash())) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "用户名或密码错误");
        }
        require(AdminStatus.ACTIVE.equals(adminUser.getStatus()), ErrorCode.FORBIDDEN, "管理员已被禁用");
        adminUserMapper.updateLastLoginAt(adminUser.getId(), LocalDateTime.now());
        return new AdminLoginResponse(
                jwtService.createAdminToken(adminUser),
                "Bearer",
                adminPermissionService.buildProfile(adminUser));
    }

    /**
     * 读取当前登录管理员资料。
     */
    @Override
    public AdminProfileResponse me(AuthenticatedUser currentUser) {
        AdminUser adminUser = adminUserMapper.findByUsername(currentUser.username());
        if (adminUser == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "登录状态已失效");
        }
        return adminPermissionService.buildProfile(adminUser);
    }
}