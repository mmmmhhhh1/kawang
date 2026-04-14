package org.example.kah.service;

import org.example.kah.common.CursorPageResponse;
import org.example.kah.dto.admin.AdminOrderDetailView;
import org.example.kah.dto.admin.AdminOrderItemView;

/**
 * 后台订单管理服务接口。
 */
public interface AdminOrderService {

    /**
     * 按游标分页查询订单。
     */
    CursorPageResponse<AdminOrderItemView> list(int size, String cursor, String status, Long productId, String keyword);

    /**
     * 查询订单详情。
     */
    AdminOrderDetailView detail(Long id);

    /**
     * 关闭成功订单。
     */
    AdminOrderDetailView close(Long id, String reason);

    /**
     * 删除订单。
     */
    void delete(Long id);
}