package org.example.kah.service;

import org.example.kah.common.PageResponse;
import org.example.kah.dto.admin.AdminOrderDetailView;
import org.example.kah.dto.admin.AdminOrderItemView;

/**
 * 后台订单管理服务接口。
 * 负责订单分页查询、详情查看和订单关闭。
 */
public interface AdminOrderService {

    /**
     * 分页查询订单。
     *
     * @param page 页码
     * @param size 每页数量
     * @param status 订单状态筛选
     * @param productId 商品筛选
     * @param keyword 订单号或联系方式关键字
     * @return 订单分页结果
     */
    PageResponse<AdminOrderItemView> list(int page, int size, String status, Long productId, String keyword);

    /**
     * 查询订单详情。
     *
     * @param id 订单主键
     * @return 订单详情视图
     */
    AdminOrderDetailView detail(Long id);

    /**
     * 关闭成功订单并释放已分配账号。
     *
     * @param id 订单主键
     * @param reason 关闭原因
     * @return 关闭后的订单详情
     */
    AdminOrderDetailView close(Long id, String reason);
}
