package org.example.kah.mapper;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.example.kah.entity.ProductAccount;

@Mapper
public interface ProductAccountMapper {

    List<ProductAccount> findAllCardKeys();

    List<ProductAccount> findCardKeysByProductId(@Param("productId") Long productId);

    List<ProductAccount> findCursorPage(Map<String, Object> params);

    ProductAccount findDetailById(@Param("id") Long id);

    List<Long> findAllCardKeyProductIds();

    ProductAccount findById(@Param("id") Long id);

    List<ProductAccount> lockAvailableCardKeys(@Param("productId") Long productId, @Param("quantity") int quantity);

    List<ProductAccount> lockByAssignedOrderId(@Param("orderId") Long orderId);

    int insert(ProductAccount account);

    long countByResourceType(@Param("resourceType") String resourceType);

    long countByProductId(@Param("productId") Long productId);

    int normalizeLegacyPool();

    int assignToOrder(@Param("id") Long id, @Param("orderId") Long orderId, @Param("assignedAt") LocalDateTime assignedAt);

    int assignBatchToOrder(@Param("ids") List<Long> ids, @Param("orderId") Long orderId, @Param("assignedAt") LocalDateTime assignedAt);

    int release(@Param("id") Long id);

    int releaseByOrderId(@Param("orderId") Long orderId);

    int updateEnableStatus(@Param("id") Long id, @Param("enableStatus") String enableStatus);

    int updateUsedStatus(@Param("id") Long id, @Param("usedStatus") String usedStatus);

    int bulkDisableCardKeysByProduct(@Param("productId") Long productId);

    int bulkDisableAllCardKeys();

    int bulkEnableCardKeysByProduct(@Param("productId") Long productId);

    int bulkEnableAllCardKeys();

    int deleteById(@Param("id") Long id);

    int deleteByProductId(@Param("productId") Long productId);
}