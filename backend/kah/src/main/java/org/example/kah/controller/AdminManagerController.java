package org.example.kah.controller;

import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.example.kah.common.ApiResponse;
import org.example.kah.dto.admin.AdminUserCreateRequest;
import org.example.kah.dto.admin.AdminUserDetailView;
import org.example.kah.dto.admin.AdminUserItemView;
import org.example.kah.dto.admin.AdminUserPermissionUpdateRequest;
import org.example.kah.entity.AdminPermissionCode;
import org.example.kah.security.AuthenticatedUser;
import org.example.kah.service.AdminManagerService;
import org.example.kah.service.AdminPermissionService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 后台管理员管理接口。
 * 仅具备创建管理员权限的管理员可访问。
 */
@RestController
@RequestMapping("/api/admin/admins")
@RequiredArgsConstructor
public class AdminManagerController {

    private final AdminManagerService adminManagerService;
    private final AdminPermissionService adminPermissionService;

    /** 查询管理员列表。 */
    @GetMapping
    public ApiResponse<List<AdminUserItemView>> list(Authentication authentication) {
        requireManageAdmins(authentication);
        return ApiResponse.success(adminManagerService.list());
    }

    /** 创建普通管理员。 */
    @PostMapping
    public ApiResponse<AdminUserDetailView> create(
            @Valid @RequestBody AdminUserCreateRequest request,
            Authentication authentication) {
        requireManageAdmins(authentication);
        return ApiResponse.success(adminManagerService.create(request));
    }

    /** 更新普通管理员权限。 */
    @PatchMapping("/{id}/permissions")
    public ApiResponse<AdminUserDetailView> updatePermissions(
            @PathVariable Long id,
            @Valid @RequestBody AdminUserPermissionUpdateRequest request,
            Authentication authentication) {
        requireManageAdmins(authentication);
        return ApiResponse.success(adminManagerService.updatePermissions(id, request.permissions()));
    }

    /** 删除普通管理员。 */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id, Authentication authentication) {
        requireManageAdmins(authentication);
        adminManagerService.delete(id);
        return ApiResponse.success();
    }

    /** 校验当前管理员是否具备创建管理员权限。 */
    private void requireManageAdmins(Authentication authentication) {
        adminPermissionService.requirePermission((AuthenticatedUser) authentication.getPrincipal(), AdminPermissionCode.CREATE_ADMIN);
    }
}