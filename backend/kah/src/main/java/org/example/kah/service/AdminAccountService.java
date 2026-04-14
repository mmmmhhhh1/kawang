package org.example.kah.service;

import java.util.List;
import org.example.kah.common.CursorPageResponse;
import org.example.kah.dto.admin.AdminAccountCreateRequest;
import org.example.kah.dto.admin.AdminAccountDetailView;
import org.example.kah.dto.admin.AdminAccountView;

/**
 * 后台卡密池管理服务接口。
 */
public interface AdminAccountService {

    /**
     * 查询卡密池列表。
     */
    List<AdminAccountView> list(Long productId, String saleStatus, String enableStatus);

    /**
     * 按游标分页查询卡密池。
     */
    CursorPageResponse<AdminAccountView> page(
            int size,
            String cursor,
            Long productId,
            String saleStatus,
            String enableStatus,
            String usedStatus,
            String keyword);

    /**
     * 查询卡密详情。
     */
    AdminAccountDetailView detail(Long id);

    /**
     * 批量创建卡密。
     */
    List<AdminAccountView> create(AdminAccountCreateRequest request);

    /**
     * 更新单条卡密启用状态。
     */
    AdminAccountView updateStatus(Long id, String enableStatus);

    /**
     * 更新单条卡密使用状态。
     */
    AdminAccountView updateUsedStatus(Long id, String usedStatus);

    /**
     * 批量停用卡密。
     */
    int bulkDisable(String scope, Long productId);

    /**
     * 批量启用卡密。
     */
    int bulkEnable(String scope, Long productId);

    /**
     * 删除单条卡密。
     */
    void delete(Long id);
}