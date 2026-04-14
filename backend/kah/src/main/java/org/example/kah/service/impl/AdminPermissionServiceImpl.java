package org.example.kah.service.impl;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.example.kah.common.BusinessException;
import org.example.kah.common.ErrorCode;
import org.example.kah.dto.admin.AdminProfileResponse;
import org.example.kah.entity.AdminPermissionCode;
import org.example.kah.entity.AdminStatus;
import org.example.kah.entity.AdminUser;
import org.example.kah.mapper.AdminUserMapper;
import org.example.kah.mapper.AdminUserPermissionMapper;
import org.example.kah.security.AuthenticatedUser;
import org.example.kah.service.AdminPermissionService;
import org.example.kah.service.impl.base.AbstractServiceSupport;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AdminPermissionServiceImpl extends AbstractServiceSupport implements AdminPermissionService {

    private final AdminUserMapper adminUserMapper;
    private final AdminUserPermissionMapper adminUserPermissionMapper;

    @Override
    public AdminProfileResponse buildProfile(AdminUser adminUser) {
        boolean isSuperAdmin = Boolean.TRUE.equals(adminUser.getIsSuperAdmin());
        List<String> permissions = isSuperAdmin
                ? AdminPermissionCode.all()
                : adminUserPermissionMapper.findByAdminUserId(adminUser.getId()).stream()
                        .map(item -> item.getPermissionCode())
                        .toList();
        return new AdminProfileResponse(
                adminUser.getId(),
                adminUser.getUsername(),
                adminUser.getDisplayName(),
                isSuperAdmin,
                permissions);
    }

    @Override
    public void requirePermission(AuthenticatedUser currentUser, String permission) {
        AdminUser adminUser = requireActiveAdmin(currentUser);
        if (Boolean.TRUE.equals(adminUser.getIsSuperAdmin())) {
            return;
        }
        boolean matched = adminUserPermissionMapper.findByAdminUserId(adminUser.getId()).stream()
                .anyMatch(item -> permission.equals(item.getPermissionCode()));
        require(matched, ErrorCode.FORBIDDEN, "当前管理员没有执行该操作的权限");
    }

    @Override
    public void requireSuperAdmin(AuthenticatedUser currentUser) {
        AdminUser adminUser = requireActiveAdmin(currentUser);
        require(Boolean.TRUE.equals(adminUser.getIsSuperAdmin()), ErrorCode.FORBIDDEN, "只有超级管理员可以执行该操作");
    }

    private AdminUser requireActiveAdmin(AuthenticatedUser currentUser) {
        AdminUser adminUser = adminUserMapper.findByUsername(currentUser.username());
        if (adminUser == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "登录状态已失效");
        }
        require(AdminStatus.ACTIVE.equals(adminUser.getStatus()), ErrorCode.FORBIDDEN, "管理员已被禁用");
        return adminUser;
    }
}