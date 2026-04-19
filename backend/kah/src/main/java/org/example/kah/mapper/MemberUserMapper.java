package org.example.kah.mapper;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.example.kah.entity.MemberUser;

@Mapper
public interface MemberUserMapper {

    MemberUser findById(@Param("id") Long id);

    MemberUser lockById(@Param("id") Long id);

    List<MemberUser> findByIds(@Param("ids") List<Long> ids);

    MemberUser findByUsername(@Param("username") String username);

    MemberUser findByEmail(@Param("email") String email);

    List<MemberUser> findAdminCursorPage(Map<String, Object> params);

    int insert(MemberUser memberUser);

    int updateStatus(@Param("id") Long id, @Param("status") String status);

    int updateLoginState(@Param("id") Long id, @Param("loginAt") LocalDateTime loginAt, @Param("lastSeenAt") LocalDateTime lastSeenAt);

    int updateLastSeenAt(@Param("id") Long id, @Param("lastSeenAt") LocalDateTime lastSeenAt);

    int mergeActivityState(@Param("id") Long id, @Param("lastLoginAt") LocalDateTime lastLoginAt, @Param("lastSeenAt") LocalDateTime lastSeenAt);

    int updateBalance(@Param("id") Long id, @Param("balance") BigDecimal balance);

    int debitBalanceIfEnough(@Param("id") Long id, @Param("amount") BigDecimal amount, @Param("beforeBalance") BigDecimal beforeBalance);
}