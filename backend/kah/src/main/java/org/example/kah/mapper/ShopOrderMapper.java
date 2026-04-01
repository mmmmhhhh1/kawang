package org.example.kah.mapper;

import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.SelectProvider;
import org.apache.ibatis.annotations.Update;
import org.example.kah.entity.ShopOrder;

/**
 * 订单主表 Mapper。
 * 负责订单创建、后台分页查询、联系方式查单以及订单关闭。
 */
@Mapper
public interface ShopOrderMapper {

    /**
     * 创建订单记录。
     *
     * @param order 订单实体
     * @return 影响行数
     */
    @Insert("""
            INSERT INTO shop_order (
                order_no, user_id, product_id, product_title_snapshot, quantity, unit_price, total_amount, buyer_name,
                buyer_contact, buyer_remark, status
            ) VALUES (
                #{orderNo}, #{userId}, #{productId}, #{productTitleSnapshot}, #{quantity}, #{unitPrice}, #{totalAmount}, #{buyerName},
                #{buyerContact}, #{buyerRemark}, #{status}
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(ShopOrder order);

    /**
     * 按主键查询订单。
     *
     * @param id 订单主键
     * @return 订单实体
     */
    @Select("""
            SELECT id, order_no, user_id, product_id, product_title_snapshot, quantity, unit_price, total_amount, buyer_name,
                   buyer_contact, buyer_remark, status, closed_reason, closed_at, created_at, updated_at
            FROM shop_order
            WHERE id = #{id}
            LIMIT 1
            """)
    ShopOrder findById(@Param("id") Long id);

    /**
     * 锁定订单记录。
     * SQL 使用 {@code FOR UPDATE}，用于关闭订单等需要保证状态一致性的事务。
     *
     * @param id 订单主键
     * @return 被锁定的订单记录
     */
    @Select("""
            SELECT id, order_no, user_id, product_id, product_title_snapshot, quantity, unit_price, total_amount, buyer_name,
                   buyer_contact, buyer_remark, status, closed_reason, closed_at, created_at, updated_at
            FROM shop_order
            WHERE id = #{id}
            FOR UPDATE
            """)
    ShopOrder lockById(@Param("id") Long id);

    /**
     * 后台分页查询订单列表。
     * 具体 SQL 由动态 SQL Provider 按筛选条件拼装。
     *
     * @param params 查询参数
     * @return 订单列表
     */
    @SelectProvider(type = ShopOrderSqlProvider.class, method = "buildAdminListSql")
    List<ShopOrder> findPage(Map<String, Object> params);

    /**
     * 统计后台分页查询总数。
     *
     * @param params 查询参数
     * @return 总记录数
     */
    @SelectProvider(type = ShopOrderSqlProvider.class, method = "buildAdminCountSql")
    long countPage(Map<String, Object> params);

    /**
     * 按联系方式和可选订单号查询订单。
     * 这是游客查单和公共查单页的核心查询入口。
     *
     * @param params 查询参数
     * @return 匹配的订单列表
     */
    @SelectProvider(type = ShopOrderSqlProvider.class, method = "buildContactQuerySql")
    List<ShopOrder> findByContact(Map<String, Object> params);

    /**
     * 查询某个会员账号绑定的订单列表。
     *
     * @param userId 会员主键
     * @return 订单集合
     */
    @Select("""
            SELECT id, order_no, user_id, product_id, product_title_snapshot, quantity, unit_price, total_amount, buyer_name,
                   buyer_contact, buyer_remark, status, closed_reason, closed_at, created_at, updated_at
            FROM shop_order
            WHERE user_id = #{userId}
            ORDER BY id DESC
            """)
    List<ShopOrder> findByUserId(@Param("userId") Long userId);

    /**
     * 关闭订单。
     *
     * @param id 订单主键
     * @param reason 关闭原因
     * @return 影响行数
     */
    @Update("""
            UPDATE shop_order
            SET status = 'CLOSED',
                closed_reason = #{reason},
                closed_at = NOW()
            WHERE id = #{id}
            """)
    int close(@Param("id") Long id, @Param("reason") String reason);
}
