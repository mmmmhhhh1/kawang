package org.example.kah.mapper;

import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.example.kah.entity.ShopOrder;

@Mapper
public interface ShopOrderMapper {

    int insert(ShopOrder order);

    ShopOrder findById(@Param("id") Long id);

    ShopOrder lockById(@Param("id") Long id);

    long countByLookupHash(@Param("lookupHash") String lookupHash);

    List<ShopOrder> findCursorPage(Map<String, Object> params);

    List<ShopOrder> findByContact(Map<String, Object> params);

    List<ShopOrder> findByUserId(@Param("userId") Long userId);

    long countByProductId(@Param("productId") Long productId);

    int close(@Param("id") Long id, @Param("reason") String reason);

    int markRefunded(@Param("id") Long id);

    int deleteById(@Param("id") Long id);
}