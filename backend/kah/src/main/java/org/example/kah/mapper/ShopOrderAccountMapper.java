package org.example.kah.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.example.kah.entity.ShopOrderAccount;

/**
 * 订单账号快照关系 Mapper。
 * 负责维护订单与账号池记录之间的绑定快照，便于追溯某个订单分配了哪些账号。
 */
@Mapper
public interface ShopOrderAccountMapper {

    /**
     * 新增订单账号关系。
     *
     * @param orderAccount 订单账号快照实体
     * @return 影响行数
     */
    @Insert("""
            INSERT INTO shop_order_account (order_id, account_id, masked_account_snapshot)
            VALUES (#{orderId}, #{accountId}, #{maskedAccountSnapshot})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(ShopOrderAccount orderAccount);

    /**
     * 查询某个订单对应的账号快照列表。
     *
     * @param orderId 订单主键
     * @return 账号快照集合
     */
    @Select("""
            SELECT id, order_id, account_id, masked_account_snapshot, created_at
            FROM shop_order_account
            WHERE order_id = #{orderId}
            ORDER BY id ASC
            """)
    List<ShopOrderAccount> findByOrderId(@Param("orderId") Long orderId);
}
