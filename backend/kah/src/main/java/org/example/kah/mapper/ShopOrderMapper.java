package org.example.kah.mapper;

import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.example.kah.entity.ShopOrder;

/**
 * 订单主表 Mapper。
 * 负责订单创建、后台分页查询、游客查单、会员订单查询、订单关闭与硬删除。
 */
@Mapper
public interface ShopOrderMapper {

    /** 创建订单记录。 */
    int insert(ShopOrder order);

    /** 按主键查询订单。 */
    ShopOrder findById(@Param("id") Long id);

    /** 锁定订单记录。 */
    ShopOrder lockById(@Param("id") Long id);

    /** 统计查单哈希是否已经存在。 */
    long countByLookupHash(@Param("lookupHash") String lookupHash);

    /** 后台分页查询订单列表。 */
    List<ShopOrder> findPage(Map<String, Object> params);

    /** 统计后台分页查询总数。 */
    long countPage(Map<String, Object> params);

    /** 按联系方式组合条件查询订单。 */
    List<ShopOrder> findByContact(Map<String, Object> params);

    /** 查询某个会员账号绑定的订单列表。 */
    List<ShopOrder> findByUserId(@Param("userId") Long userId);

    /** 统计某个商品关联的历史订单数。 */
    long countByProductId(@Param("productId") Long productId);

    /** 关闭订单。 */
    int close(@Param("id") Long id, @Param("reason") String reason);

    /** 硬删除订单。 */
    int deleteById(@Param("id") Long id);
}