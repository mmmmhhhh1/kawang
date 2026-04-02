package org.example.kah.entity;

/**
 * 卡密售卖状态常量。
 * 用于表示卡密是否已经被订单占用并售出。
 */
public final class SaleStatus {

    /** 未售出，仍可参与库存统计。 */
    public static final String UNSOLD = "UNSOLD";

    /** 已售出，表示已经绑定到某个订单。 */
    public static final String SOLD = "SOLD";

    private SaleStatus() {
    }
}