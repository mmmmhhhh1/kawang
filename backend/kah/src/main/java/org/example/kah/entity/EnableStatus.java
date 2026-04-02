package org.example.kah.entity;

/**
 * 卡密启用状态常量。
 * 启用状态与售卖状态分离，支持已售卡密继续被管理员停用。
 */
public final class EnableStatus {

    /** 启用状态，可参与可售库存统计。 */
    public static final String ENABLED = "ENABLED";

    /** 停用状态，不参与可售库存统计。 */
    public static final String DISABLED = "DISABLED";

    private EnableStatus() {
    }
}