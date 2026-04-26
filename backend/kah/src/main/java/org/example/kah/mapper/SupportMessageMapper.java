package org.example.kah.mapper;

import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.example.kah.entity.SupportMessage;

@Mapper
public interface SupportMessageMapper {

    int insert(SupportMessage message);

    SupportMessage findById(@Param("id") Long id);

    List<SupportMessage> findSessionCursorPage(Map<String, Object> params);

    List<SupportMessage> findSessionAfterCursorPage(Map<String, Object> params);

    SupportMessage findByIdWithSession(@Param("id") Long id);
}
