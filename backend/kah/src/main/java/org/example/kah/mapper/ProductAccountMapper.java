package org.example.kah.mapper;

import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.example.kah.entity.ProductAccount;

/**
 * 卡密池 Mapper。
 * 负责卡密查询、导入、并发锁定、启停切换、批量启停和删除等操作。
 */
@Mapper
public interface ProductAccountMapper {

    /** 查询全部卡密池记录。 */
    List<ProductAccount> findAllCardKeys();

    /** 查询某个商品下的全部卡密池记录。 */
    List<ProductAccount> findCardKeysByProductId(@Param("productId") Long productId);

    /** 按主键查询卡密记录。 */
    ProductAccount findById(@Param("id") Long id);

    /** 锁定某个商品下足量可售卡密。 */
    List<ProductAccount> lockAvailableCardKeys(@Param("productId") Long productId, @Param("quantity") int quantity);

    /** 锁定某个订单已分配的全部卡密。 */
    List<ProductAccount> lockByAssignedOrderId(@Param("orderId") Long orderId);

    /** 新增卡密记录。 */
    int insert(ProductAccount account);

    /** 统计某种资源类型的记录数量。 */
    long countByResourceType(@Param("resourceType") String resourceType);

    /** 统计某个商品下的资源记录数量。 */
    long countByProductId(@Param("productId") Long productId);

    /** 将旧账号池记录统一归并为历史资源并停用。 */
    int normalizeLegacyPool();

    /** 把卡密分配到订单。 */
    int assignToOrder(@Param("id") Long id, @Param("orderId") Long orderId, @Param("assignedAt") LocalDateTime assignedAt);

    /** 释放已绑定订单的卡密。 */
    int release(@Param("id") Long id);

    /** 更新单条卡密启用状态。 */
    int updateEnableStatus(@Param("id") Long id, @Param("enableStatus") String enableStatus);

    /** 按商品批量停用卡密。 */
    int bulkDisableCardKeysByProduct(@Param("productId") Long productId);

    /** 全站批量停用卡密。 */
    int bulkDisableAllCardKeys();

    /** 按商品批量启用卡密。 */
    int bulkEnableCardKeysByProduct(@Param("productId") Long productId);

    /** 全站批量启用卡密。 */
    int bulkEnableAllCardKeys();

    /** 删除单条卡密。 */
    int deleteById(@Param("id") Long id);

    /** 删除某个商品下的全部资源记录。 */
    int deleteByProductId(@Param("productId") Long productId);
}