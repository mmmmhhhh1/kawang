package org.example.kah.mapper;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.example.kah.entity.SupportSession;

@Mapper
public interface SupportSessionMapper {

    SupportSession findById(@Param("id") Long id);

    SupportSession findByIdWithMember(@Param("id") Long id);

    SupportSession findByMemberIdBasic(@Param("memberId") Long memberId);

    SupportSession findByMemberIdWithMember(@Param("memberId") Long memberId);

    SupportSession lockById(@Param("id") Long id);

    int insertIgnore(@Param("memberId") Long memberId);

    int applyMemberMessage(
            @Param("id") Long id,
            @Param("preview") String preview,
            @Param("createdAt") LocalDateTime createdAt);

    int applyAdminMessage(
            @Param("id") Long id,
            @Param("preview") String preview,
            @Param("createdAt") LocalDateTime createdAt);

    int markMemberRead(@Param("id") Long id);

    int markAdminRead(@Param("id") Long id);

    List<SupportSession> findAdminCursorPage(Map<String, Object> params);

    List<SupportSession> findAdminUpdatedAfter(Map<String, Object> params);
}
