package org.example.kah.mapper;

import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.example.kah.entity.AdminUser;

/**
 * 后台管理员 Mapper。
 * 负责管理员账号查询、初始化写入以及登录时间更新。
 */
@Mapper
public interface AdminUserMapper {

    /**
     * 按用户名查询管理员账号。
     *
     * @param username 管理员用户名
     * @return 管理员实体
     */
    @Select("""
            SELECT id, username, password_hash, status, display_name, last_login_at, created_at, updated_at
            FROM admin_user
            WHERE username = #{username}
            LIMIT 1
            """)
    AdminUser findByUsername(@Param("username") String username);

    /**
     * 新增管理员账号。
     *
     * @param adminUser 管理员实体
     * @return 影响行数
     */
    @Insert("""
            INSERT INTO admin_user (username, password_hash, status, display_name)
            VALUES (#{username}, #{passwordHash}, #{status}, #{displayName})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(AdminUser adminUser);

    /**
     * 更新管理员基础信息。
     *
     * @param adminUser 管理员实体
     * @return 影响行数
     */
    @Update("""
            UPDATE admin_user
            SET password_hash = #{passwordHash},
                status = #{status},
                display_name = #{displayName}
            WHERE id = #{id}
            """)
    int updateProfile(AdminUser adminUser);

    /**
     * 更新管理员最近登录时间。
     *
     * @param id 管理员主键
     * @param loginAt 登录时间
     * @return 影响行数
     */
    @Update("""
            UPDATE admin_user
            SET last_login_at = #{loginAt}
            WHERE id = #{id}
            """)
    int updateLastLoginAt(@Param("id") Long id, @Param("loginAt") LocalDateTime loginAt);
}
