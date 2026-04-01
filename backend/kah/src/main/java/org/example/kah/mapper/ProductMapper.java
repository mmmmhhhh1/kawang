package org.example.kah.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.example.kah.entity.ShopProduct;

/**
 * 商品表 Mapper。
 * 负责商品基础 CRUD、前台可售商品查询，以及库存/销量统计回刷。
 */
@Mapper
public interface ProductMapper {

    /**
     * 查询前台可售商品列表。
     *
     * @return 所有处于上架状态的商品
     */
    @Select("""
            SELECT id, sku, title, vendor, plan_name, description, price, available_stock, sold_count, status, sort_order, created_at, updated_at
            FROM shop_product
            WHERE status = 'ACTIVE'
            ORDER BY sort_order ASC, id DESC
            """)
    List<ShopProduct> findActiveProducts();

    /**
     * 查询后台商品列表。
     *
     * @return 全量商品记录
     */
    @Select("""
            SELECT id, sku, title, vendor, plan_name, description, price, available_stock, sold_count, status, sort_order, created_at, updated_at
            FROM shop_product
            ORDER BY sort_order ASC, id DESC
            """)
    List<ShopProduct> findAllProducts();

    /**
     * 按主键查询商品。
     *
     * @param id 商品主键
     * @return 商品实体，不存在时返回 {@code null}
     */
    @Select("""
            SELECT id, sku, title, vendor, plan_name, description, price, available_stock, sold_count, status, sort_order, created_at, updated_at
            FROM shop_product
            WHERE id = #{id}
            LIMIT 1
            """)
    ShopProduct findById(@Param("id") Long id);

    /**
     * 按 SKU 查询商品。
     *
     * @param sku 商品 SKU
     * @return 商品实体，不存在时返回 {@code null}
     */
    @Select("""
            SELECT id, sku, title, vendor, plan_name, description, price, available_stock, sold_count, status, sort_order, created_at, updated_at
            FROM shop_product
            WHERE sku = #{sku}
            LIMIT 1
            """)
    ShopProduct findBySku(@Param("sku") String sku);

    /**
     * 锁定单个商品记录。
     * SQL 使用 {@code FOR UPDATE}，用于下单事务中读取最新库存，防止并发超卖。
     *
     * @param id 商品主键
     * @return 被锁定的商品记录
     */
    @Select("""
            SELECT id, sku, title, vendor, plan_name, description, price, available_stock, sold_count, status, sort_order, created_at, updated_at
            FROM shop_product
            WHERE id = #{id}
            FOR UPDATE
            """)
    ShopProduct lockById(@Param("id") Long id);

    /**
     * 新增商品。
     *
     * @param product 商品实体
     * @return 影响行数
     */
    @Insert("""
            INSERT INTO shop_product (sku, title, vendor, plan_name, description, price, available_stock, sold_count, status, sort_order)
            VALUES (#{sku}, #{title}, #{vendor}, #{planName}, #{description}, #{price}, #{availableStock}, #{soldCount}, #{status}, #{sortOrder})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(ShopProduct product);

    /**
     * 更新商品基础信息。
     *
     * @param product 商品实体
     * @return 影响行数
     */
    @Update("""
            UPDATE shop_product
            SET sku = #{sku},
                title = #{title},
                vendor = #{vendor},
                plan_name = #{planName},
                description = #{description},
                price = #{price},
                status = #{status},
                sort_order = #{sortOrder}
            WHERE id = #{id}
            """)
    int update(ShopProduct product);

    /**
     * 更新商品上架状态。
     *
     * @param id 商品主键
     * @param status 目标状态
     * @return 影响行数
     */
    @Update("""
            UPDATE shop_product
            SET status = #{status}
            WHERE id = #{id}
            """)
    int updateStatus(@Param("id") Long id, @Param("status") String status);

    /**
     * 直接调整库存与销量。
     * 当前主流程主要使用 {@link #syncStats()} 回刷，但这里保留增量调整能力供扩展使用。
     *
     * @param id 商品主键
     * @param stockDelta 库存增量，正数为增加，负数为扣减
     * @param soldDelta 销量增量，正数为增加，负数为减少
     * @return 影响行数
     */
    @Update("""
            UPDATE shop_product
            SET available_stock = available_stock + #{stockDelta},
                sold_count = sold_count + #{soldDelta}
            WHERE id = #{id}
            """)
    int adjustStats(@Param("id") Long id, @Param("stockDelta") int stockDelta, @Param("soldDelta") int soldDelta);

    /**
     * 基于账号池状态和成功订单重新回刷所有商品的库存与销量。
     * 这是兜底统计入口，用于避免库存和销量在异常场景下发生漂移。
     *
     * @return 影响行数
     */
    @Update("""
            UPDATE shop_product p
            SET available_stock = (
                    SELECT COUNT(*)
                    FROM product_account pa
                    WHERE pa.product_id = p.id AND pa.status = 'AVAILABLE'
                ),
                sold_count = (
                    SELECT COALESCE(SUM(o.quantity), 0)
                    FROM shop_order o
                    WHERE o.product_id = p.id AND o.status = 'SUCCESS'
                )
            """)
    int syncStats();
}
