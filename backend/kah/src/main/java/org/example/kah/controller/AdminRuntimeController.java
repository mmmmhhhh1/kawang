package org.example.kah.controller;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.example.kah.common.ApiResponse;
import org.example.kah.dto.admin.AdminRuntimeDetailsView;
import org.example.kah.dto.admin.AdminRuntimeOverviewView;
import org.example.kah.service.AdminRuntimeService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/runtime")
@RequiredArgsConstructor
public class AdminRuntimeController {

    private final AdminRuntimeService adminRuntimeService;

    @GetMapping("/overview")
    public ApiResponse<AdminRuntimeOverviewView> overview() {
        return ApiResponse.success(adminRuntimeService.getOverview());
    }

    @GetMapping("/details")
    public ApiResponse<AdminRuntimeDetailsView> details(@RequestParam(required = false) List<String> keys) {
        return ApiResponse.success(adminRuntimeService.getDetails(keys));
    }
}
