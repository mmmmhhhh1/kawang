package org.example.kah.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.kah.common.ApiResponse;
import org.example.kah.common.CursorPageResponse;
import org.example.kah.dto.admin.AdminRechargeDecisionRequest;
import org.example.kah.dto.admin.AdminRechargeDetailView;
import org.example.kah.dto.admin.AdminRechargeItemView;
import org.example.kah.security.AuthenticatedUser;
import org.example.kah.service.AdminRechargeService;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/recharges")
@RequiredArgsConstructor
public class AdminRechargeController {

    private final AdminRechargeService adminRechargeService;

    @GetMapping
    public ApiResponse<CursorPageResponse<AdminRechargeItemView>> list(
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String userKeyword) {
        return ApiResponse.success(adminRechargeService.list(size, cursor, status, userKeyword));
    }

    @GetMapping("/{id}")
    public ApiResponse<AdminRechargeDetailView> detail(@PathVariable Long id) {
        return ApiResponse.success(adminRechargeService.detail(id));
    }

    @GetMapping("/{id}/screenshot")
    public ResponseEntity<Resource> screenshot(@PathVariable Long id) {
        Resource resource = adminRechargeService.loadScreenshot(id);
        MediaType mediaType = MediaTypeFactory.getMediaType(resource).orElse(MediaType.APPLICATION_OCTET_STREAM);
        return ResponseEntity.ok().contentType(mediaType).body(resource);
    }

    @PatchMapping("/{id}/approve")
    public ApiResponse<AdminRechargeDetailView> approve(@PathVariable Long id, Authentication authentication) {
        AuthenticatedUser currentUser = (AuthenticatedUser) authentication.getPrincipal();
        return ApiResponse.success(adminRechargeService.approve(id, currentUser));
    }

    @PatchMapping("/{id}/reject")
    public ApiResponse<AdminRechargeDetailView> reject(
            @PathVariable Long id,
            @Valid @RequestBody AdminRechargeDecisionRequest request,
            Authentication authentication) {
        AuthenticatedUser currentUser = (AuthenticatedUser) authentication.getPrincipal();
        return ApiResponse.success(adminRechargeService.reject(id, request.reason(), currentUser));
    }
}