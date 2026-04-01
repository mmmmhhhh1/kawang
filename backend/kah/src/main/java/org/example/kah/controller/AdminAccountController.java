package org.example.kah.controller;

import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.example.kah.common.ApiResponse;
import org.example.kah.dto.admin.AdminAccountCreateRequest;
import org.example.kah.dto.admin.AdminAccountStatusRequest;
import org.example.kah.dto.admin.AdminAccountView;
import org.example.kah.service.AdminAccountService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 后台账号池管理接口。
 * 仅管理员可访问，用于查看、导入和禁用商品账号池数据。
 */
@RestController
@RequestMapping("/api/admin/accounts")
@RequiredArgsConstructor
public class AdminAccountController {

    private final AdminAccountService adminAccountService;

    /**
     * 查询账号池列表。
     *
     * @param productId 商品筛选条件
     * @param status 账号状态筛选条件
     * @return 账号池视图列表
     */
    @GetMapping
    public ApiResponse<List<AdminAccountView>> list(
            @RequestParam(required = false) Long productId,
            @RequestParam(required = false) String status) {
        return ApiResponse.success(adminAccountService.list(productId, status));
    }

    /**
     * 创建账号池记录。
     *
     * @param request 账号导入请求
     * @return 创建后的账号视图列表
     */
    @PostMapping
    public ApiResponse<List<AdminAccountView>> create(@Valid @RequestBody AdminAccountCreateRequest request) {
        return ApiResponse.success(adminAccountService.create(request));
    }

    /**
     * 批量创建账号池记录。
     * 该接口与 create 共享同一套导入逻辑，仅保留更直观的批量导入路径。
     *
     * @param request 账号导入请求
     * @return 创建后的账号视图列表
     */
    @PostMapping("/batch")
    public ApiResponse<List<AdminAccountView>> batchCreate(@Valid @RequestBody AdminAccountCreateRequest request) {
        return ApiResponse.success(adminAccountService.create(request));
    }

    /**
     * 更新账号状态。
     *
     * @param id 账号主键
     * @param request 状态变更请求
     * @return 更新后的账号视图
     */
    @PatchMapping("/{id}/status")
    public ApiResponse<AdminAccountView> updateStatus(@PathVariable Long id, @Valid @RequestBody AdminAccountStatusRequest request) {
        return ApiResponse.success(adminAccountService.updateStatus(id, request.status().trim()));
    }
}
