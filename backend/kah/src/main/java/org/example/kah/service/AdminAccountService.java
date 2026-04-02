package org.example.kah.service;

import java.util.List;
import org.example.kah.dto.admin.AdminAccountCreateRequest;
import org.example.kah.dto.admin.AdminAccountView;

/**
 * 后台卡密池管理服务接口。
 * 负责卡密列表、批量导入、启停切换、批量启停和删除。
 */
public interface AdminAccountService {

    /** 查询卡密池列表。 */
    List<AdminAccountView> list(Long productId, String saleStatus, String enableStatus);

    /** 批量创建卡密。 */
    List<AdminAccountView> create(AdminAccountCreateRequest request);

    /** 更新单条卡密启用状态。 */
    AdminAccountView updateStatus(Long id, String enableStatus);

    /** 批量停用卡密。 */
    int bulkDisable(String scope, Long productId);

    /** 批量启用卡密。 */
    int bulkEnable(String scope, Long productId);

    /** 删除单条卡密。 */
    void delete(Long id);
}