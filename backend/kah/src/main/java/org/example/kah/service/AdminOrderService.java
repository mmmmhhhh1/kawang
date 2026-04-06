package org.example.kah.service;

import org.example.kah.common.PageResponse;
import org.example.kah.dto.admin.AdminOrderDetailView;
import org.example.kah.dto.admin.AdminOrderItemView;

/**
 * 后台订单管理服务接口。
 * 负责订单分页查询、详情查看、订单关闭和订单删除。
 */
public interface AdminOrderService {

    /** 分页查询订单。 */
    PageResponse<AdminOrderItemView> list(int page, int size, String status, Long productId, String keyword);

    /** 查询订单详情。 */
    AdminOrderDetailView detail(Long id);

    /** 关闭成功订单。 */
    AdminOrderDetailView close(Long id, String reason);

    /** 硬删除订单及其卡密快照。 */
    void delete(Long id);
}