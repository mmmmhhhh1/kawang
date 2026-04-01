package org.example.kah.entity;

/**
 * 订单状态常量。
 */
public final class OrderStatus {

    /** 成功状态，表示订单已完成模拟履约并占用账号。 */
    public static final String SUCCESS = "SUCCESS";

    /** 关闭状态，表示订单已被后台关闭并回滚库存。 */
    public static final String CLOSED = "CLOSED";

    private OrderStatus() {
    }
}
