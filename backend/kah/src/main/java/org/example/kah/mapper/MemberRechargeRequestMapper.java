package org.example.kah.mapper;

import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.example.kah.entity.MemberRechargeRequest;

@Mapper
public interface MemberRechargeRequestMapper {

    int insert(MemberRechargeRequest request);

    MemberRechargeRequest findById(@Param("id") Long id);

    MemberRechargeRequest lockById(@Param("id") Long id);

    List<MemberRechargeRequest> findMemberCursorPage(Map<String, Object> params);

    List<MemberRechargeRequest> findAdminCursorPage(Map<String, Object> params);

    int approve(@Param("id") Long id, @Param("reviewedBy") Long reviewedBy);

    int reject(@Param("id") Long id, @Param("reviewedBy") Long reviewedBy, @Param("reason") String reason);
}