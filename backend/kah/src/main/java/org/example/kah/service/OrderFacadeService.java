package org.example.kah.service;

import org.example.kah.dto.publicapi.MemberOrderPageView;
import org.example.kah.dto.publicapi.CreateOrderRequest;
import org.example.kah.dto.publicapi.OrderCreatedResponse;
import org.example.kah.dto.publicapi.OrderQueryView;
import org.example.kah.dto.publicapi.QueryOrdersRequest;
import org.example.kah.security.AuthenticatedUser;

/**
 * 前台订单流程服务接口。
 * 负责下单、按联系方式查单以及按会员查询已绑定订单。
 */
public interface OrderFacadeService {

    /**
     * 创建订单。
     *
     * @param request 下单请求
     * @param currentUser 当前登录会员，可为空
     * @return 订单创建结果
     */
    OrderCreatedResponse create(CreateOrderRequest request, AuthenticatedUser currentUser);

    /**
     * 按联系方式查询订单。
     *
     * @param request 查单请求
     * @return 匹配订单集合
     */
    java.util.List<OrderQueryView> queryByContact(QueryOrdersRequest request);

    /**
     * 查询当前会员已绑定订单。
     *
     * @param userId 会员主键
     * @return 已绑定订单集合
     */
    MemberOrderPageView listByUser(Long userId, int size, String cursor);
}
