package org.example.kah.service;

import java.util.List;
import org.example.kah.dto.admin.AdminAccountCreateRequest;
import org.example.kah.dto.admin.AdminAccountView;

/**
 * 后台账号池管理服务接口。
 * 负责账号列表、批量导入与账号状态切换。
 */
public interface AdminAccountService {

    /**
     * 查询账号池列表。
     *
     * @param productId 商品主键，可为空
     * @param status 账号状态，可为空
     * @return 账号视图集合
     */
    List<AdminAccountView> list(Long productId, String status);

    /**
     * 批量创建账号。
     *
     * @param request 批量导入请求
     * @return 创建后的账号视图集合
     */
    List<AdminAccountView> create(AdminAccountCreateRequest request);

    /**
     * 更新账号状态。
     *
     * @param id 账号主键
     * @param status 目标状态
     * @return 更新后的账号视图
     */
    AdminAccountView updateStatus(Long id, String status);
}
