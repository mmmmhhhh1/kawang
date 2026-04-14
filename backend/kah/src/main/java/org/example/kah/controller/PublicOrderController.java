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
 * 前台订单接口。
 */
@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class PublicOrderController {

    private final OrderFacadeService orderFacadeService;

    /**
     * 使用会员余额创建订单。
     */
    @PostMapping
    public ApiResponse<OrderCreatedResponse> createOrder(
            @Valid @RequestBody CreateOrderRequest request,
            Authentication authentication) {
        AuthenticatedUser currentUser = (AuthenticatedUser) authentication.getPrincipal();
        return ApiResponse.success(orderFacadeService.create(request, currentUser));
    }

    /**
     * 历史游客订单查询接口。
     */
    @PostMapping("/query")
    public ApiResponse<List<OrderQueryView>> queryOrders(@Valid @RequestBody QueryOrdersRequest request) {
        return ApiResponse.success(orderFacadeService.queryByContact(request));
    }
}