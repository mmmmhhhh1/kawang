package org.example.kah.service;

import java.util.List;
import org.example.kah.dto.admin.AdminUserCreateRequest;
import org.example.kah.dto.admin.AdminUserDetailView;
import org.example.kah.dto.admin.AdminUserItemView;

/**
 * 后台管理员管理服务接口。
 * 负责管理员列表、创建、权限变更和删除。
 */
public interface AdminManagerService {

    /** 查询管理员列表。 */
    List<AdminUserItemView> list();

    /** 创建普通管理员。 */
    AdminUserDetailView create(AdminUserCreateRequest request);

    /** 更新普通管理员权限。 */
    AdminUserDetailView updatePermissions(Long id, List<String> permissions);

    /** 删除普通管理员。 */
    void delete(Long id);
}