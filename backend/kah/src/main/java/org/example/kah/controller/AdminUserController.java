package org.example.kah.controller;

import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.example.kah.common.ApiResponse;
import org.example.kah.common.CursorPageResponse;
import org.example.kah.dto.admin.AdminMemberActivityView;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
public class AdminUserController {

    private final AdminMemberService adminMemberService;
    private final AdminPermissionService adminPermissionService;

    @GetMapping
    public ApiResponse<List<AdminMemberListView>> list() {
        return ApiResponse.success(adminMemberService.list());
    }

    @GetMapping("/page")
    public ApiResponse<CursorPageResponse<AdminMemberListView>> page(
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status) {
        return ApiResponse.success(adminMemberService.page(size, cursor, keyword, status));
    }

    @GetMapping("/{id}")
    public ApiResponse<AdminMemberDetailView> detail(@PathVariable Long id) {
        return ApiResponse.success(adminMemberService.detail(id));
    }

    @GetMapping("/activity")
    public ApiResponse<List<AdminMemberActivityView>> listActivities(@RequestParam List<Long> ids) {
        return ApiResponse.success(adminMemberService.listActivities(ids));
    }

    @GetMapping("/{id}/activity")
    public ApiResponse<AdminMemberActivityView> activity(@PathVariable Long id) {
        return ApiResponse.success(adminMemberService.activity(id));
    }

    @PatchMapping("/{id}/status")
    public ApiResponse<AdminMemberListView> updateStatus(
            @PathVariable Long id,
            @Valid @RequestBody AdminMemberStatusRequest request,
            Authentication authentication) {
        adminPermissionService.requirePermission((AuthenticatedUser) authentication.getPrincipal(), AdminPermissionCode.DISABLE_USER);
        return ApiResponse.success(adminMemberService.updateStatus(id, request.status().trim()));
    }
}