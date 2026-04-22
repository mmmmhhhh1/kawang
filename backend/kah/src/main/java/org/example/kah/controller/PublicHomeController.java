package org.example.kah.controller;

import lombok.RequiredArgsConstructor;
import org.example.kah.common.ApiResponse;
import org.example.kah.dto.publicapi.HomeView;
import org.example.kah.service.NoticeService;
import org.example.kah.service.ProductFacadeService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/home")
@RequiredArgsConstructor
public class PublicHomeController {

    private final ProductFacadeService productFacadeService;
    private final NoticeService noticeService;

    @GetMapping
    public ApiResponse<HomeView> getHome() {
        return ApiResponse.success(new HomeView(
                productFacadeService.listProducts(),
                noticeService.listPublished()));
    }
}
