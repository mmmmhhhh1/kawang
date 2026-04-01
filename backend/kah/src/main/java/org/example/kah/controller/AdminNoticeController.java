package org.example.kah.controller;

import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.example.kah.common.ApiResponse;
import org.example.kah.dto.admin.AdminNoticeSaveRequest;
import org.example.kah.dto.admin.AdminNoticeStatusRequest;
import org.example.kah.dto.admin.AdminNoticeView;
import org.example.kah.service.NoticeService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 后台公告管理接口。
 */
@RestController
@RequestMapping("/api/admin/notices")
@RequiredArgsConstructor
public class AdminNoticeController {

    private final NoticeService noticeService;

    /**
     * 查询后台公告列表。
     *
     * @return 后台公告视图列表
     */
    @GetMapping
    public ApiResponse<List<AdminNoticeView>> list() {
        return ApiResponse.success(noticeService.listAdmin());
    }

    /**
     * 创建公告。
     *
     * @param request 公告保存请求
     * @return 创建后的公告视图
     */
    @PostMapping
    public ApiResponse<AdminNoticeView> create(@Valid @RequestBody AdminNoticeSaveRequest request) {
        return ApiResponse.success(noticeService.create(request));
    }

    /**
     * 更新公告内容。
     *
     * @param id 公告主键
     * @param request 公告保存请求
     * @return 更新后的公告视图
     */
    @PutMapping("/{id}")
    public ApiResponse<AdminNoticeView> update(@PathVariable Long id, @Valid @RequestBody AdminNoticeSaveRequest request) {
        return ApiResponse.success(noticeService.update(id, request));
    }

    /**
     * 更新公告显示状态。
     *
     * @param id 公告主键
     * @param request 状态请求
     * @return 更新后的公告视图
     */
    @PatchMapping("/{id}/status")
    public ApiResponse<AdminNoticeView> updateStatus(@PathVariable Long id, @Valid @RequestBody AdminNoticeStatusRequest request) {
        return ApiResponse.success(noticeService.updateStatus(id, request.status()));
    }
}
