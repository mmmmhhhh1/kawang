package org.example.kah.entity;

import java.time.LocalDateTime;
import lombok.Data;

/**
 * 订单与账号关系实体。
 * 对应表 {@code shop_order_account}，用于记录订单分配到的账号快照。
 */
@Data
public class ShopOrderAccount {

    /** 关系记录主键。 */
    private Long id;

    /** 订单主键。 */
    private Long orderId;

    /** 账号池记录主键。 */
    private Long accountId;

    /** 下单时的脱敏账号快照。 */
    private String maskedAccountSnapshot;

    /** 创建时间。 */
    private LocalDateTime createdAt;
}
