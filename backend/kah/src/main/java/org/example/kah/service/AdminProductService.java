package org.example.kah.service;

import java.util.List;
import org.example.kah.dto.admin.AdminProductSaveRequest;
import org.example.kah.dto.admin.AdminProductView;

/**
 * 后台商品管理服务接口。
 * 负责商品列表、创建、编辑、上下架和删除。
 */
public interface AdminProductService {

    /** 查询后台商品列表。 */
    List<AdminProductView> list();

    /** 创建商品。 */
    AdminProductView create(AdminProductSaveRequest request);

    /** 更新商品。 */
    AdminProductView update(Long id, AdminProductSaveRequest request);

    /** 更新商品上架状态。 */
    AdminProductView updateStatus(Long id, String status);

    /** 删除商品。 */
    void delete(Long id);
}