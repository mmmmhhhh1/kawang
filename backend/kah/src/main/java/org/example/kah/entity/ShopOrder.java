package org.example.kah.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 订单实体。
 * 对应表 {@code shop_order}，保存下单快照、金额信息、买家信息与订单状态。
 */
@Data
public class ShopOrder {

    /** 订单主键。 */
    private Long id;

    /** 外部可见订单号。 */
    private String orderNo;

    /** 绑定的会员主键，游客下单时为空。 */
    private Long userId;

    /** 商品主键。 */
    private Long productId;

    /** 下单时的商品标题快照。 */
    private String productTitleSnapshot;

    /** 购买数量。 */
    private Integer quantity;

    /** 下单时单价快照。 */
    private BigDecimal unitPrice;

    /** 订单总金额。 */
    private BigDecimal totalAmount;

    /** 买家姓名。 */
    private String buyerName;

    /** 联系方式，用于游客查单与买家识别。 */
    private String buyerContact;

    /** 买家备注。 */
    private String buyerRemark;

    /** 订单状态，例如 SUCCESS / CLOSED。 */
    private String status;

    /** 关闭订单的原因。 */
    private String closedReason;

    /** 关闭时间。 */
    private LocalDateTime closedAt;

    /** 创建时间。 */
    private LocalDateTime createdAt;

    /** 最后更新时间。 */
    private LocalDateTime updatedAt;
}
