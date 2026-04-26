package org.example.kah.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.example.kah.entity.AdminMobileDevice;

@Mapper
public interface AdminMobileDeviceMapper {

    int upsert(AdminMobileDevice device);

    int deleteByAdminAndToken(
            @Param("adminUserId") Long adminUserId,
            @Param("vendor") String vendor,
            @Param("deviceToken") String deviceToken);

    List<String> findEnabledTokensForSuperAdmins(@Param("vendor") String vendor);

    List<String> findEnabledTokensForActiveAdmins(@Param("vendor") String vendor);
}
