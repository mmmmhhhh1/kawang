package org.example.kah.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

@RestController
@RequestMapping("/api/admin/auth")
@RequiredArgsConstructor
@Slf4j
public class AdminAuthController {

    private final AdminAuthService adminAuthService;

    @PostMapping("/login")
    public ApiResponse<AdminLoginResponse> login(
            @Valid @RequestBody AdminLoginRequest request,
            HttpServletRequest httpServletRequest) {
        log.info(
                "[admin-login] request received username={} remoteAddr={} userAgent={}",
                request.username(),
                httpServletRequest.getRemoteAddr(),
                httpServletRequest.getHeader("User-Agent"));
        AdminLoginResponse response = adminAuthService.login(request.username(), request.password());
        log.info(
                "[admin-login] request completed username={} adminId={} superAdmin={}",
                request.username(),
                response.profile().id(),
                response.profile().isSuperAdmin());
        return ApiResponse.success(response);
    }

    @GetMapping("/me")
    public ApiResponse<AdminProfileResponse> me(Authentication authentication) {
        AuthenticatedUser currentUser = (AuthenticatedUser) authentication.getPrincipal();
        log.info("[admin-login] /me requested username={} userId={}", currentUser.username(), currentUser.userId());
        return ApiResponse.success(adminAuthService.me(currentUser));
    }
}
