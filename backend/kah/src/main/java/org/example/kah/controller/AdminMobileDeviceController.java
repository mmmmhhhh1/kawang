package org.example.kah.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.kah.common.ApiResponse;
import org.example.kah.dto.admin.AdminMobileDeviceRegisterRequest;
import org.example.kah.dto.admin.AdminMobileDeviceUnregisterRequest;
import org.example.kah.security.AuthenticatedUser;
import org.example.kah.service.AdminMobileDeviceService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/mobile/devices")
@RequiredArgsConstructor
public class AdminMobileDeviceController {

    private final AdminMobileDeviceService adminMobileDeviceService;

    @PostMapping("/register")
    public ApiResponse<Void> register(Authentication authentication, @Valid @RequestBody AdminMobileDeviceRegisterRequest request) {
        AuthenticatedUser currentUser = (AuthenticatedUser) authentication.getPrincipal();
        adminMobileDeviceService.register(currentUser, request.vendor(), request.deviceToken(), request.deviceName());
        return ApiResponse.success();
    }

    @PostMapping("/unregister")
    public ApiResponse<Void> unregister(Authentication authentication, @Valid @RequestBody AdminMobileDeviceUnregisterRequest request) {
        AuthenticatedUser currentUser = (AuthenticatedUser) authentication.getPrincipal();
        adminMobileDeviceService.unregister(currentUser, request.vendor(), request.deviceToken());
        return ApiResponse.success();
    }
}
