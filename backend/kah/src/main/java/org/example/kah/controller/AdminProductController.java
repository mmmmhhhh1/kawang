package org.example.kah.controller;

import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.example.kah.common.ApiResponse;
import org.example.kah.common.CursorPageResponse;
import org.example.kah.dto.admin.AdminProductOptionView;
import org.example.kah.dto.admin.AdminProductSaveRequest;
import org.example.kah.dto.admin.AdminProductStatusRequest;
import org.example.kah.dto.admin.AdminProductView;
import org.example.kah.entity.AdminPermissionCode;
import org.example.kah.security.AuthenticatedUser;
import org.example.kah.service.AdminPermissionService;
import org.example.kah.service.AdminProductService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/products")
@RequiredArgsConstructor
public class AdminProductController {

    private final AdminProductService adminProductService;
    private final AdminPermissionService adminPermissionService;

    @GetMapping("/page")
    public ApiResponse<CursorPageResponse<AdminProductView>> page(
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status) {
        return ApiResponse.success(adminProductService.page(size, cursor, keyword, status));
    }

    @GetMapping("/options")
    public ApiResponse<List<AdminProductOptionView>> options(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(adminProductService.searchOptions(keyword, size));
    }

    @PostMapping
    public ApiResponse<AdminProductView> create(@Valid @RequestBody AdminProductSaveRequest request) {
        return ApiResponse.success(adminProductService.create(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<AdminProductView> update(@PathVariable Long id, @Valid @RequestBody AdminProductSaveRequest request) {
        return ApiResponse.success(adminProductService.update(id, request));
    }

    @PatchMapping("/{id}/status")
    public ApiResponse<AdminProductView> updateStatus(@PathVariable Long id, @Valid @RequestBody AdminProductStatusRequest request) {
        return ApiResponse.success(adminProductService.updateStatus(id, request.status().trim()));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id, Authentication authentication) {
        adminPermissionService.requirePermission((AuthenticatedUser) authentication.getPrincipal(), AdminPermissionCode.DELETE_PRODUCT);
        adminProductService.delete(id);
        return ApiResponse.success();
    }
}