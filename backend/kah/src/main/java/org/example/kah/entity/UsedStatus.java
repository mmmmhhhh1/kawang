package org.example.kah.entity;

/**
 * 卡密使用状态常量。
 * 用于标记某张已售卡密是否已经被最终用户消耗使用。
 */
public final class UsedStatus {

    /** 未使用。 */
    public static final String UNUSED = "UNUSED";

    /** 已使用。 */
    public static final String USED = "USED";

    private UsedStatus() {
    }
}
