package org.example.kah.mapper;

import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.example.kah.entity.PaymentQrConfig;

@Mapper
public interface PaymentQrConfigMapper {

    List<PaymentQrConfig> findAll();

    List<PaymentQrConfig> findCursorPage(Map<String, Object> params);

    PaymentQrConfig findById(@Param("id") Long id);

    PaymentQrConfig findActive();

    int insert(PaymentQrConfig config);

    int disableAll();

    int activate(@Param("id") Long id, @Param("activatedBy") Long activatedBy);

    int disable(@Param("id") Long id);
}