package org.example.kah.controller;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.example.kah.common.ApiResponse;
import org.example.kah.dto.publicapi.ProductView;
import org.example.kah.service.ProductFacadeService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 前台公开商品接口。
 */
@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class PublicProductController {

    private final ProductFacadeService productFacadeService;

    /**
     * 查询前台可售商品列表。
     *
     * @return 商品列表
     */
    @GetMapping
    public ApiResponse<List<ProductView>> listProducts() {
        return ApiResponse.success(productFacadeService.listProducts());
    }

    /**
     * 查询单个商品详情。
     *
     * @param id 商品主键
     * @return 商品详情
     */
    @GetMapping("/{id}")
    public ApiResponse<ProductView> getProduct(@PathVariable Long id) {
        return ApiResponse.success(productFacadeService.getProduct(id));
    }
}
