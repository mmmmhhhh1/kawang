package org.example.kah.mapper;

import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.example.kah.cache.ProductStatsCacheItem;
import org.example.kah.entity.ShopProduct;

@Mapper
public interface ProductMapper {

    List<ShopProduct> findActiveProducts();

    List<ShopProduct> findAllProducts();

    List<ShopProduct> findAdminCursorPage(Map<String, Object> params);

    ShopProduct findById(@Param("id") Long id);

    ShopProduct findBySku(@Param("sku") String sku);

    ShopProduct lockById(@Param("id") Long id);

    int insert(ShopProduct product);

    int update(ShopProduct product);

    int updateStatus(@Param("id") Long id, @Param("status") String status);

    int adjustStats(@Param("id") Long id, @Param("stockDelta") int stockDelta, @Param("soldDelta") int soldDelta);

    int syncStats();

    int syncStatsByProductId(@Param("id") Long id);

    List<ProductStatsCacheItem> findStatsByIds(@Param("productIds") List<Long> productIds);

    int deleteById(@Param("id") Long id);
}