package org.example.kah.entity;

import java.time.LocalDateTime;
import lombok.Data;

/**
 * 订单资源快照实体。
 * 对应表 {@code shop_order_account}，用于记录订单分配到的卡密快照。
 */
@Data
public class ShopOrderAccount {

    /** 关系记录主键。 */
    private Long id;

    /** 订单主键。 */
    private Long orderId;

    /** 资源池记录主键。 */
    private Long accountId;

    /** 旧账号池兼容的脱敏快照。 */
    private String maskedAccountSnapshot;

    /** 下单时写入的卡密密文快照。 */
    private String cardKeyCiphertextSnapshot;

    /** 当前卡密启用状态，来源于联表查询。 */
    private String enableStatus;

    /** 创建时间。 */
    private LocalDateTime createdAt;
}