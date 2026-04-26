package org.example.kah.service.impl;

import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminAuthServiceImpl extends AbstractServiceSupport implements AdminAuthService {

    private final AdminUserMapper adminUserMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AdminPermissionService adminPermissionService;

    @Override
    public AdminLoginResponse login(String username, String password) {
        String normalizedUsername = trim(username);
        log.info("[admin-login] loading admin user username={}", normalizedUsername);
        AdminUser adminUser = adminUserMapper.findByUsername(normalizedUsername);
        if (adminUser == null) {
            log.warn("[admin-login] admin user not found username={}", normalizedUsername);
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "用户名或密码错误");
        }

        boolean passwordMatched = passwordEncoder.matches(password, adminUser.getPasswordHash());
        log.info(
                "[admin-login] password check username={} matched={} status={} superAdmin={}",
                normalizedUsername,
                passwordMatched,
                adminUser.getStatus(),
                adminUser.getIsSuperAdmin());
        if (!passwordMatched) {
            log.warn("[admin-login] password mismatch username={}", normalizedUsername);
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "用户名或密码错误");
        }

        require(AdminStatus.ACTIVE.equals(adminUser.getStatus()), ErrorCode.FORBIDDEN, "管理员已被禁用");
        adminUserMapper.updateLastLoginAt(adminUser.getId(), LocalDateTime.now());
        log.info("[admin-login] last login updated username={} adminId={}", normalizedUsername, adminUser.getId());

        String token = jwtService.createAdminToken(adminUser);
        log.info("[admin-login] token created username={} adminId={} tokenLength={}", normalizedUsername, adminUser.getId(), token.length());

        AdminProfileResponse profile = adminPermissionService.buildProfile(adminUser);
        log.info(
                "[admin-login] profile built username={} adminId={} permissions={}",
                normalizedUsername,
                profile.id(),
                profile.permissions().size());
        return new AdminLoginResponse(token, "Bearer", profile);
    }

    @Override
    public AdminProfileResponse me(AuthenticatedUser currentUser) {
        log.info("[admin-login] loading /me profile username={} userId={}", currentUser.username(), currentUser.userId());
        AdminUser adminUser = adminUserMapper.findByUsername(currentUser.username());
        if (adminUser == null) {
            log.warn("[admin-login] /me profile missing username={} userId={}", currentUser.username(), currentUser.userId());
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "登录状态已失效");
        }
        return adminPermissionService.buildProfile(adminUser);
    }
}
