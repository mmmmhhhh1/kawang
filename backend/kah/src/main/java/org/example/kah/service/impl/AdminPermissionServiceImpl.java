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

/**
 * {@link AdminPermissionService} 默认实现。
 * 负责装配管理员资料响应，并在控制器进入具体业务前完成权限校验。
 */
@Service
@RequiredArgsConstructor
public class AdminPermissionServiceImpl extends AbstractServiceSupport implements AdminPermissionService {

    private final AdminUserMapper adminUserMapper;
    private final AdminUserPermissionMapper adminUserPermissionMapper;

    /**
     * 将管理员实体装配为前端所需的权限资料。
     */
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

    /**
     * 校验当前管理员是否拥有指定权限。
     */
    @Override
    public void requirePermission(AuthenticatedUser currentUser, String permission) {
        AdminUser adminUser = adminUserMapper.findByUsername(currentUser.username());
        if (adminUser == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "登录状态已失效");
        }
        require(AdminStatus.ACTIVE.equals(adminUser.getStatus()), ErrorCode.FORBIDDEN, "管理员已被禁用");
        if (Boolean.TRUE.equals(adminUser.getIsSuperAdmin())) {
            return;
        }
        boolean matched = adminUserPermissionMapper.findByAdminUserId(adminUser.getId()).stream()
                .anyMatch(item -> permission.equals(item.getPermissionCode()));
        require(matched, ErrorCode.FORBIDDEN, "当前管理员没有执行该操作的权限");
    }
}