package org.example.kah.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.example.kah.entity.ShopOrderAccount;

/**
 * 订单资源快照 Mapper。
 * 负责维护订单与卡密快照之间的绑定关系。
 */
@Mapper
public interface ShopOrderAccountMapper {

    /** 新增订单资源快照关系。 */
    @Insert("""
            INSERT INTO shop_order_account (order_id, account_id, masked_account_snapshot, card_key_ciphertext_snapshot)
            VALUES (#{orderId}, #{accountId}, #{maskedAccountSnapshot}, #{cardKeyCiphertextSnapshot})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(ShopOrderAccount orderAccount);

    /**
     * 查询某个订单对应的资源快照列表。
     * 联表带出当前启用状态，以便前台和后台看到卡密是否被后续停用。
     */
    @Select("""
            SELECT soa.id, soa.order_id, soa.account_id, soa.masked_account_snapshot, soa.card_key_ciphertext_snapshot,
                   pa.enable_status, soa.created_at
            FROM shop_order_account soa
            LEFT JOIN product_account pa ON pa.id = soa.account_id
            WHERE soa.order_id = #{orderId}
            ORDER BY soa.id ASC
            """)
    List<ShopOrderAccount> findByOrderId(@Param("orderId") Long orderId);

    /** 删除某个订单的全部资源快照。 */
    @Delete("""
            DELETE FROM shop_order_account
            WHERE order_id = #{orderId}
            """)
    int deleteByOrderId(@Param("orderId") Long orderId);
}