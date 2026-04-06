package org.example.kah.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.kah.common.ApiResponse;
import org.example.kah.common.PageResponse;
import org.example.kah.dto.admin.AdminOrderCloseRequest;
import org.example.kah.dto.admin.AdminOrderDetailView;
import org.example.kah.dto.admin.AdminOrderItemView;
import org.example.kah.entity.AdminPermissionCode;
import org.example.kah.security.AuthenticatedUser;
import org.example.kah.service.AdminOrderService;
import org.example.kah.service.AdminPermissionService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 后台订单管理接口。
 * 仅管理员可访问，用于分页查询订单、查看订单详情、关闭订单和删除订单。
 */
@RestController
@RequestMapping("/api/admin/orders")
@RequiredArgsConstructor
public class AdminOrderController {

    private final AdminOrderService adminOrderService;
    private final AdminPermissionService adminPermissionService;

    /**
     * 分页查询订单列表。
     */
    @GetMapping
    public ApiResponse<PageResponse<AdminOrderItemView>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long productId,
            @RequestParam(required = false) String keyword) {
        return ApiResponse.success(adminOrderService.list(page, size, status, productId, keyword));
    }

    /**
     * 查询订单详情。
     */
    @GetMapping("/{id}")
    public ApiResponse<AdminOrderDetailView> detail(@PathVariable Long id) {
        return ApiResponse.success(adminOrderService.detail(id));
    }

    /**
     * 关闭成功订单。
     */
    @PatchMapping("/{id}/close")
    public ApiResponse<AdminOrderDetailView> close(@PathVariable Long id, @Valid @RequestBody AdminOrderCloseRequest request) {
        return ApiResponse.success(adminOrderService.close(id, request.reason().trim()));
    }

    /**
     * 删除订单。
     */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id, Authentication authentication) {
        adminPermissionService.requirePermission((AuthenticatedUser) authentication.getPrincipal(), AdminPermissionCode.DELETE_ORDER);
        adminOrderService.delete(id);
        return ApiResponse.success();
    }
}