package org.example.kah.mapper;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.example.kah.entity.AdminUser;

@Mapper
public interface AdminUserMapper {

    AdminUser findByUsername(@Param("username") String username);

    AdminUser findById(@Param("id") Long id);

    List<AdminUser> findAdminCursorPage(Map<String, Object> params);

    int insert(AdminUser adminUser);

    int updateProfile(AdminUser adminUser);

    int updateLastLoginAt(@Param("id") Long id, @Param("loginAt") LocalDateTime loginAt);

    int deleteById(@Param("id") Long id);
}