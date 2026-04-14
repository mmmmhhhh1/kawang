package org.example.kah.controller;

import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.example.kah.common.ApiResponse;
import org.example.kah.common.CursorPageResponse;
import org.example.kah.dto.admin.AdminAccountBulkDisableRequest;
import org.example.kah.dto.admin.AdminAccountCreateRequest;
import org.example.kah.dto.admin.AdminAccountDetailView;
import org.example.kah.dto.admin.AdminAccountStatusRequest;
import org.example.kah.dto.admin.AdminAccountUsedStatusRequest;
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

@RestController
@RequestMapping("/api/admin/accounts")
@RequiredArgsConstructor
public class AdminAccountController {

    private final AdminAccountService adminAccountService;

    @GetMapping
    public ApiResponse<CursorPageResponse<AdminAccountView>> page(
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Long productId,
            @RequestParam(required = false) String saleStatus,
            @RequestParam(required = false) String enableStatus,
            @RequestParam(required = false) String usedStatus,
            @RequestParam(required = false) String keyword) {
        return ApiResponse.success(adminAccountService.page(size, cursor, productId, saleStatus, enableStatus, usedStatus, keyword));
    }

    @GetMapping("/all")
    public ApiResponse<List<AdminAccountView>> list(
            @RequestParam(required = false) Long productId,
            @RequestParam(required = false) String saleStatus,
            @RequestParam(required = false) String enableStatus) {
        return ApiResponse.success(adminAccountService.list(productId, saleStatus, enableStatus));
    }

    @GetMapping("/{id}")
    public ApiResponse<AdminAccountDetailView> detail(@PathVariable Long id) {
        return ApiResponse.success(adminAccountService.detail(id));
    }

    @PostMapping
    public ApiResponse<List<AdminAccountView>> create(@Valid @RequestBody AdminAccountCreateRequest request) {
        return ApiResponse.success(adminAccountService.create(request));
    }

    @PostMapping("/batch")
    public ApiResponse<List<AdminAccountView>> batchCreate(@Valid @RequestBody AdminAccountCreateRequest request) {
        return ApiResponse.success(adminAccountService.create(request));
    }

    @PatchMapping("/{id}/status")
    public ApiResponse<AdminAccountView> updateStatus(@PathVariable Long id, @Valid @RequestBody AdminAccountStatusRequest request) {
        return ApiResponse.success(adminAccountService.updateStatus(id, request.enableStatus().trim()));
    }

    @PatchMapping("/{id}/used-status")
    public ApiResponse<AdminAccountView> updateUsedStatus(@PathVariable Long id, @Valid @RequestBody AdminAccountUsedStatusRequest request) {
        return ApiResponse.success(adminAccountService.updateUsedStatus(id, request.usedStatus().trim()));
    }

    @PatchMapping("/bulk-disable")
    public ApiResponse<Integer> bulkDisable(@Valid @RequestBody AdminAccountBulkDisableRequest request) {
        return ApiResponse.success(adminAccountService.bulkDisable(request.scope().trim(), request.productId()));
    }

    @PatchMapping("/bulk-enable")
    public ApiResponse<Integer> bulkEnable(@Valid @RequestBody AdminAccountBulkDisableRequest request) {
        return ApiResponse.success(adminAccountService.bulkEnable(request.scope().trim(), request.productId()));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        adminAccountService.delete(id);
        return ApiResponse.success();
    }
}