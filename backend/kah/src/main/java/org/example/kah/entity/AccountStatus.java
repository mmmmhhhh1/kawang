package org.example.kah.entity;

/**
 * 账号池状态常量。
 * 用于标识账号是否可分配、已分配或被禁用。
 */
public final class AccountStatus {

    /** 可用状态，表示账号可用于下单分配。 */
    public static final String AVAILABLE = "AVAILABLE";

    /** 已分配状态，表示账号已经绑定到订单。 */
    public static final String ASSIGNED = "ASSIGNED";

    /** 禁用状态，表示账号不可继续出售。 */
    public static final String DISABLED = "DISABLED";

    private AccountStatus() {
    }
}
