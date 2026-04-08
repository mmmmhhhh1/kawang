package org.example.kah.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.example.kah.cache.ProductStatsCacheItem;
import org.example.kah.entity.ShopProduct;

/**
 * 商品表 Mapper。
 * 复杂 SQL 已迁移到 XML 中维护，这里只保留接口语义声明。
 */
@Mapper
public interface ProductMapper {

    /** 查询前台可售商品列表。 */
    List<ShopProduct> findActiveProducts();

    /** 查询后台商品列表。 */
    List<ShopProduct> findAllProducts();

    /** 按主键查询商品。 */
    ShopProduct findById(@Param("id") Long id);

    /** 按 SKU 查询商品。 */
    ShopProduct findBySku(@Param("sku") String sku);

    /** 锁定商品记录，供下单和库存回滚事务使用。 */
    ShopProduct lockById(@Param("id") Long id);

    /** 新增商品。 */
    int insert(ShopProduct product);

    /** 更新商品基础信息。 */
    int update(ShopProduct product);

    /** 更新商品上架状态。 */
    int updateStatus(@Param("id") Long id, @Param("status") String status);

    /** 直接调整库存与销量。 */
    int adjustStats(@Param("id") Long id, @Param("stockDelta") int stockDelta, @Param("soldDelta") int soldDelta);

    /** 基于卡密池重新同步全部商品的库存与销量统计。 */
    int syncStats();

    /** 仅同步单商品的库存与销量统计。 */
    int syncStatsByProductId(@Param("id") Long id);

    /** 批量查询商品当前库存与销量。 */
    List<ProductStatsCacheItem> findStatsByIds(@Param("productIds") List<Long> productIds);

    /** 删除商品。 */
    int deleteById(@Param("id") Long id);
}