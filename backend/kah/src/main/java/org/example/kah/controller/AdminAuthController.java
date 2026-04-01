package org.example.kah.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.kah.common.ApiResponse;
import org.example.kah.dto.admin.AdminLoginRequest;
import org.example.kah.dto.admin.AdminLoginResponse;
import org.example.kah.dto.admin.AdminProfileResponse;
import org.example.kah.security.AuthenticatedUser;
import org.example.kah.service.AdminAuthService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 后台管理员认证接口。
 */
@RestController
@RequestMapping("/api/admin/auth")
@RequiredArgsConstructor
public class AdminAuthController {

    private final AdminAuthService adminAuthService;

    /**
     * 管理员登录接口。
     *
     * @param request 登录请求
     * @return 登录结果与 JWT
     */
    @PostMapping("/login")
    public ApiResponse<AdminLoginResponse> login(@Valid @RequestBody AdminLoginRequest request) {
        return ApiResponse.success(adminAuthService.login(request.username(), request.password()));
    }

    /**
     * 当前管理员资料接口。
     *
     * @param authentication Spring Security 当前认证对象
     * @return 当前管理员资料
     */
    @GetMapping("/me")
    public ApiResponse<AdminProfileResponse> me(Authentication authentication) {
        return ApiResponse.success(adminAuthService.me((AuthenticatedUser) authentication.getPrincipal()));
    }
}
