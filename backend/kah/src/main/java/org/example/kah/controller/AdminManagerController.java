package org.example.kah.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.kah.common.ApiResponse;
import org.example.kah.common.CursorPageResponse;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/admins")
@RequiredArgsConstructor
public class AdminManagerController {

    private final AdminManagerService adminManagerService;
    private final AdminPermissionService adminPermissionService;

    @GetMapping("/page")
    public ApiResponse<CursorPageResponse<AdminUserItemView>> page(
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) String keyword,
            Authentication authentication) {
        requireManageAdmins(authentication);
        return ApiResponse.success(adminManagerService.page(size, cursor, keyword));
    }

    @PostMapping
    public ApiResponse<AdminUserDetailView> create(
            @Valid @RequestBody AdminUserCreateRequest request,
            Authentication authentication) {
        requireManageAdmins(authentication);
        return ApiResponse.success(adminManagerService.create(request));
    }

    @PatchMapping("/{id}/permissions")
    public ApiResponse<AdminUserDetailView> updatePermissions(
            @PathVariable Long id,
            @Valid @RequestBody AdminUserPermissionUpdateRequest request,
            Authentication authentication) {
        requireManageAdmins(authentication);
        return ApiResponse.success(adminManagerService.updatePermissions(id, request.permissions()));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id, Authentication authentication) {
        requireManageAdmins(authentication);
        adminManagerService.delete(id);
        return ApiResponse.success();
    }

    private void requireManageAdmins(Authentication authentication) {
        adminPermissionService.requirePermission((AuthenticatedUser) authentication.getPrincipal(), AdminPermissionCode.CREATE_ADMIN);
    }
}