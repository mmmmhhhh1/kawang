package org.example.kah.service;

import java.util.List;
import org.example.kah.dto.admin.AdminProductSaveRequest;
import org.example.kah.dto.admin.AdminProductView;

/**
 * 后台商品管理服务接口。
 * 负责商品列表读取、创建、编辑以及上下架状态切换。
 */
public interface AdminProductService {

    /**
     * 查询后台商品列表。
     *
     * @return 后台商品视图集合
     */
    List<AdminProductView> list();

    /**
     * 创建商品。
     *
     * @param request 商品创建请求
     * @return 创建后的商品视图
     */
    AdminProductView create(AdminProductSaveRequest request);

    /**
     * 更新指定商品。
     *
     * @param id 商品主键
     * @param request 商品更新请求
     * @return 更新后的商品视图
     */
    AdminProductView update(Long id, AdminProductSaveRequest request);

    /**
     * 更新商品上下架状态。
     *
     * @param id 商品主键
     * @param status 目标状态
     * @return 更新后的商品视图
     */
    AdminProductView updateStatus(Long id, String status);
}
