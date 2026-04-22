package org.example.kah.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.kah.common.ApiResponse;
import org.example.kah.common.CursorPageResponse;
import org.example.kah.dto.admin.AdminNoticeSaveRequest;
import org.example.kah.dto.admin.AdminNoticeStatusRequest;
import org.example.kah.dto.admin.AdminNoticeView;
import org.example.kah.service.NoticeService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/notices")
@RequiredArgsConstructor
public class AdminNoticeController {

    private final NoticeService noticeService;

    @GetMapping("/page")
    public ApiResponse<CursorPageResponse<AdminNoticeView>> page(
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status) {
        return ApiResponse.success(noticeService.pageAdmin(size, cursor, keyword, status));
    }

    @PostMapping
    public ApiResponse<AdminNoticeView> create(@Valid @RequestBody AdminNoticeSaveRequest request) {
        return ApiResponse.success(noticeService.create(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<AdminNoticeView> update(@PathVariable Long id, @Valid @RequestBody AdminNoticeSaveRequest request) {
        return ApiResponse.success(noticeService.update(id, request));
    }

    @PatchMapping("/{id}/status")
    public ApiResponse<AdminNoticeView> updateStatus(@PathVariable Long id, @Valid @RequestBody AdminNoticeStatusRequest request) {
        return ApiResponse.success(noticeService.updateStatus(id, request.status()));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        noticeService.delete(id);
        return ApiResponse.success(null);
    }
}
