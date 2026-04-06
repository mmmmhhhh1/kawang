package org.example.kah.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.example.kah.entity.AdminUserPermission;

/**
 * 管理员权限关系 Mapper。
 * 负责查询、重建和删除普通管理员的权限集。
 */
@Mapper
public interface AdminUserPermissionMapper {

    /** 查询某个管理员的权限列表。 */
    List<AdminUserPermission> findByAdminUserId(@Param("adminUserId") Long adminUserId);

    /** 删除某个管理员的全部权限。 */
    int deleteByAdminUserId(@Param("adminUserId") Long adminUserId);

    /** 批量插入管理员权限。 */
    int insertBatch(@Param("adminUserId") Long adminUserId, @Param("permissions") List<String> permissions);
}