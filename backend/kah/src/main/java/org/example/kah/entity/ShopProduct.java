package org.example.kah.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 商品实体。
 * 对应表 {@code shop_product}，用于描述商城对外售卖的 AI 会员商品。
 */
@Data
public class ShopProduct {

    /** 商品主键。 */
    private Long id;

    /** 商品唯一 SKU。 */
    private String sku;

    /** 商品标题。 */
    private String title;

    /** 商品所属厂商或品牌。 */
    private String vendor;

    /** 商品套餐名称。 */
    private String planName;

    /** 商品描述。 */
    private String description;

    /** 商品单价。 */
    private BigDecimal price;

    /** 当前可售库存。 */
    private Integer availableStock;

    /** 当前累计已售数量。 */
    private Integer soldCount;

    /** 商品状态，例如 ACTIVE / INACTIVE。 */
    private String status;

    /** 商品排序值，值越小越靠前。 */
    private Integer sortOrder;

    /** 创建时间。 */
    private LocalDateTime createdAt;

    /** 最后更新时间。 */
    private LocalDateTime updatedAt;
}
