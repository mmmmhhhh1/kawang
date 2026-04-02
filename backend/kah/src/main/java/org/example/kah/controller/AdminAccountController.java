package org.example.kah.controller;

import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.example.kah.common.ApiResponse;
import org.example.kah.dto.admin.AdminAccountBulkDisableRequest;
import org.example.kah.dto.admin.AdminAccountCreateRequest;
import org.example.kah.dto.admin.AdminAccountStatusRequest;
import org.example.kah.dto.admin.AdminAccountView;
import org.example.kah.service.AdminAccountService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 后台卡密池管理接口。
 * 仅管理员可访问，用于查看、导入、启停、删除和批量启停商品卡密。
 */
@RestController
@RequestMapping("/api/admin/accounts")
@RequiredArgsConstructor
public class AdminAccountController {

    private final AdminAccountService adminAccountService;

    /** 查询卡密池列表。 */
    @GetMapping
    public ApiResponse<List<AdminAccountView>> list(
            @RequestParam(required = false) Long productId,
            @RequestParam(required = false) String saleStatus,
            @RequestParam(required = false) String enableStatus) {
        return ApiResponse.success(adminAccountService.list(productId, saleStatus, enableStatus));
    }

    /** 创建卡密记录。 */
    @PostMapping
    public ApiResponse<List<AdminAccountView>> create(@Valid @RequestBody AdminAccountCreateRequest request) {
        return ApiResponse.success(adminAccountService.create(request));
    }

    /** 批量创建卡密记录。 */
    @PostMapping("/batch")
    public ApiResponse<List<AdminAccountView>> batchCreate(@Valid @RequestBody AdminAccountCreateRequest request) {
        return ApiResponse.success(adminAccountService.create(request));
    }

    /** 更新单条卡密启用状态。 */
    @PatchMapping("/{id}/status")
    public ApiResponse<AdminAccountView> updateStatus(@PathVariable Long id, @Valid @RequestBody AdminAccountStatusRequest request) {
        return ApiResponse.success(adminAccountService.updateStatus(id, request.enableStatus().trim()));
    }

    /** 批量停用卡密。 */
    @PatchMapping("/bulk-disable")
    public ApiResponse<Integer> bulkDisable(@Valid @RequestBody AdminAccountBulkDisableRequest request) {
        return ApiResponse.success(adminAccountService.bulkDisable(request.scope().trim(), request.productId()));
    }

    /** 批量启用卡密。 */
    @PatchMapping("/bulk-enable")
    public ApiResponse<Integer> bulkEnable(@Valid @RequestBody AdminAccountBulkDisableRequest request) {
        return ApiResponse.success(adminAccountService.bulkEnable(request.scope().trim(), request.productId()));
    }

    /** 删除单条卡密。 */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        adminAccountService.delete(id);
        return ApiResponse.success();
    }
}