package org.example.kah.mapper;

import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.example.kah.entity.MemberUser;

/**
 * 前台会员 Mapper。
 * 负责会员注册、登录查询和最近登录时间更新。
 */
@Mapper
public interface MemberUserMapper {

    /**
     * 按主键查询会员。
     *
     * @param id 会员主键
     * @return 会员实体
     */
    @Select("""
            SELECT id, username, password_hash, status, last_login_at, created_at, updated_at
            FROM member_user
            WHERE id = #{id}
            LIMIT 1
            """)
    MemberUser findById(@Param("id") Long id);

    /**
     * 按用户名查询会员。
     *
     * @param username 会员用户名
     * @return 会员实体
     */
    @Select("""
            SELECT id, username, password_hash, status, last_login_at, created_at, updated_at
            FROM member_user
            WHERE username = #{username}
            LIMIT 1
            """)
    MemberUser findByUsername(@Param("username") String username);

    /**
     * 新增会员。
     *
     * @param memberUser 会员实体
     * @return 影响行数
     */
    @Insert("""
            INSERT INTO member_user (username, password_hash, status)
            VALUES (#{username}, #{passwordHash}, #{status})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(MemberUser memberUser);

    /**
     * 更新会员最近登录时间。
     *
     * @param id 会员主键
     * @param loginAt 登录时间
     * @return 影响行数
     */
    @Update("""
            UPDATE member_user
            SET last_login_at = #{loginAt}
            WHERE id = #{id}
            """)
    int updateLastLoginAt(@Param("id") Long id, @Param("loginAt") LocalDateTime loginAt);
}
