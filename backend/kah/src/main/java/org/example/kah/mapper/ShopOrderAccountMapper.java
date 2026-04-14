package org.example.kah.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.example.kah.entity.ShopOrderAccount;

@Mapper
public interface ShopOrderAccountMapper {

    @Insert("""
            INSERT INTO shop_order_account (order_id, account_id, masked_account_snapshot, card_key_ciphertext_snapshot)
            VALUES (#{orderId}, #{accountId}, #{maskedAccountSnapshot}, #{cardKeyCiphertextSnapshot})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(ShopOrderAccount orderAccount);

    @Insert({
            "<script>",
            "INSERT INTO shop_order_account (order_id, account_id, masked_account_snapshot, card_key_ciphertext_snapshot) VALUES",
            "<foreach collection='items' item='item' separator=','>",
            "(#{item.orderId}, #{item.accountId}, #{item.maskedAccountSnapshot}, #{item.cardKeyCiphertextSnapshot})",
            "</foreach>",
            "</script>"
    })
    int batchInsert(@Param("items") List<ShopOrderAccount> items);

    @Select("""
            SELECT soa.id, soa.order_id, soa.account_id, soa.masked_account_snapshot, soa.card_key_ciphertext_snapshot,
                   pa.enable_status, pa.used_status, soa.created_at
            FROM shop_order_account soa
            LEFT JOIN product_account pa ON pa.id = soa.account_id
            WHERE soa.order_id = #{orderId}
            ORDER BY soa.id ASC
            """)
    List<ShopOrderAccount> findByOrderId(@Param("orderId") Long orderId);

    @Delete("""
            DELETE FROM shop_order_account
            WHERE order_id = #{orderId}
            """)
    int deleteByOrderId(@Param("orderId") Long orderId);
}