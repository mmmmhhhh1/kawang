package org.example.kah.controller;

import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.example.kah.common.ApiResponse;
import org.example.kah.dto.admin.AdminMemberDetailView;
import org.example.kah.dto.admin.AdminMemberListView;
import org.example.kah.dto.admin.AdminMemberStatusRequest;
import org.example.kah.entity.AdminPermissionCode;
import org.example.kah.security.AuthenticatedUser;
import org.example.kah.service.AdminMemberService;
import org.example.kah.service.AdminPermissionService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 后台会员管理接口。
 * 管理员可查看会员列表和详情，具备指定权限时可停用或启用会员账号。
 */
@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
public class AdminUserController {

    private final AdminMemberService adminMemberService;
    private final AdminPermissionService adminPermissionService;

    /** 查询会员列表。 */
    @GetMapping
    public ApiResponse<List<AdminMemberListView>> list() {
        return ApiResponse.success(adminMemberService.list());
    }

    /** 查询会员详情。 */
    @GetMapping("/{id}")
    public ApiResponse<AdminMemberDetailView> detail(@PathVariable Long id) {
        return ApiResponse.success(adminMemberService.detail(id));
    }

    /** 更新会员状态。 */
    @PatchMapping("/{id}/status")
    public ApiResponse<AdminMemberListView> updateStatus(
            @PathVariable Long id,
            @Valid @RequestBody AdminMemberStatusRequest request,
            Authentication authentication) {
        adminPermissionService.requirePermission((AuthenticatedUser) authentication.getPrincipal(), AdminPermissionCode.DISABLE_USER);
        return ApiResponse.success(adminMemberService.updateStatus(id, request.status().trim()));
    }
}