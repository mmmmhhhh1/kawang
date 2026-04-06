package org.example.kah.mapper;

import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.example.kah.entity.AdminUser;

/**
 * 后台管理员 Mapper。
 * 负责管理员账号查询、创建、删除、初始化同步以及最近登录时间维护。
 */
@Mapper
public interface AdminUserMapper {

    /** 按用户名查询管理员。 */
    AdminUser findByUsername(@Param("username") String username);

    /** 按主键查询管理员。 */
    AdminUser findById(@Param("id") Long id);

    /** 查询全部管理员列表。 */
    List<AdminUser> findAll();

    /** 新增管理员。 */
    int insert(AdminUser adminUser);

    /** 更新管理员基础信息。 */
    int updateProfile(AdminUser adminUser);

    /** 更新最近登录时间。 */
    int updateLastLoginAt(@Param("id") Long id, @Param("loginAt") LocalDateTime loginAt);

    /** 删除管理员。 */
    int deleteById(@Param("id") Long id);
}