package org.example.kah.controller;

import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.example.kah.common.ApiResponse;
import org.example.kah.dto.publicapi.CreateOrderRequest;
import org.example.kah.dto.publicapi.OrderCreatedResponse;
import org.example.kah.dto.publicapi.OrderQueryView;
import org.example.kah.dto.publicapi.QueryOrdersRequest;
import org.example.kah.security.AuthenticatedUser;
import org.example.kah.service.OrderFacadeService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 前台公开订单接口。
 * 游客和登录用户都可以下单，也都能按联系方式查单。
 */
@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class PublicOrderController {

    private final OrderFacadeService orderFacadeService;

    /**
     * 前台下单接口。
     * 游客和登录会员都可以下单，登录会员会自动绑定 userId。
     *
     * @param request 下单请求
     * @param authentication 当前认证对象，可为空
     * @return 订单创建结果
     */
    @PostMapping
    public ApiResponse<OrderCreatedResponse> createOrder(
            @Valid @RequestBody CreateOrderRequest request,
            Authentication authentication) {
        AuthenticatedUser currentUser = authentication == null ? null : (AuthenticatedUser) authentication.getPrincipal();
        return ApiResponse.success(orderFacadeService.create(request, currentUser));
    }

    /**
     * 按联系方式查询订单接口。
     *
     * @param request 查单请求
     * @return 匹配的订单列表
     */
    @PostMapping("/query")
    public ApiResponse<List<OrderQueryView>> queryOrders(@Valid @RequestBody QueryOrdersRequest request) {
        return ApiResponse.success(orderFacadeService.queryByContact(request));
    }
}
