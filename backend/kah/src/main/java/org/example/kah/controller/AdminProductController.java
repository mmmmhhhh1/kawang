package org.example.kah.controller;

import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.example.kah.common.ApiResponse;
import org.example.kah.dto.admin.AdminProductSaveRequest;
import org.example.kah.dto.admin.AdminProductStatusRequest;
import org.example.kah.dto.admin.AdminProductView;
import org.example.kah.service.AdminProductService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 后台商品管理接口。
 * 仅管理员可访问，用于维护商品主表信息、上下架状态和删除商品。
 */
@RestController
@RequestMapping("/api/admin/products")
@RequiredArgsConstructor
public class AdminProductController {

    private final AdminProductService adminProductService;

    /** 查询后台商品列表。 */
    @GetMapping
    public ApiResponse<List<AdminProductView>> list() {
        return ApiResponse.success(adminProductService.list());
    }

    /** 创建商品。 */
    @PostMapping
    public ApiResponse<AdminProductView> create(@Valid @RequestBody AdminProductSaveRequest request) {
        return ApiResponse.success(adminProductService.create(request));
    }

    /** 更新商品。 */
    @PutMapping("/{id}")
    public ApiResponse<AdminProductView> update(@PathVariable Long id, @Valid @RequestBody AdminProductSaveRequest request) {
        return ApiResponse.success(adminProductService.update(id, request));
    }

    /** 切换商品状态。 */
    @PatchMapping("/{id}/status")
    public ApiResponse<AdminProductView> updateStatus(@PathVariable Long id, @Valid @RequestBody AdminProductStatusRequest request) {
        return ApiResponse.success(adminProductService.updateStatus(id, request.status().trim()));
    }

    /** 删除商品。 */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        adminProductService.delete(id);
        return ApiResponse.success();
    }
}