package org.example.kah.mapper;

import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.example.kah.entity.MemberUser;

/**
 * 前台会员 Mapper。
 */
@Mapper
public interface MemberUserMapper {

    /** 按主键查询会员。 */
    MemberUser findById(@Param("id") Long id);

    /** 按主键批量查询会员。 */
    List<MemberUser> findByIds(@Param("ids") List<Long> ids);

    /** 按用户名查询会员。 */
    MemberUser findByUsername(@Param("username") String username);

    /** 按邮箱查询会员。 */
    MemberUser findByEmail(@Param("email") String email);

    /** 查询全部会员列表。 */
    List<MemberUser> findAll();

    /** 新增会员。 */
    int insert(MemberUser memberUser);

    /** 更新会员状态。 */
    int updateStatus(@Param("id") Long id, @Param("status") String status);

    /** 更新最近登录时间和最近活跃时间。 */
    int updateLoginState(@Param("id") Long id, @Param("loginAt") LocalDateTime loginAt, @Param("lastSeenAt") LocalDateTime lastSeenAt);

    /** 仅更新最近活跃时间。 */
    int updateLastSeenAt(@Param("id") Long id, @Param("lastSeenAt") LocalDateTime lastSeenAt);

    /** 合并 Redis 回写的活动信息。 */
    int mergeActivityState(@Param("id") Long id, @Param("lastLoginAt") LocalDateTime lastLoginAt, @Param("lastSeenAt") LocalDateTime lastSeenAt);
}