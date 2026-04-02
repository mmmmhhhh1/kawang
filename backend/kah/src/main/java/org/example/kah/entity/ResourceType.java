package org.example.kah.entity;

/**
 * 资源池类型常量。
 * 用于区分新卡密记录与旧账号池迁移记录。
 */
public final class ResourceType {

    /** 新版卡密池记录。 */
    public static final String CARD_KEY = "CARD_KEY";

    /** 历史账号池迁移记录。 */
    public static final String LEGACY_ACCOUNT = "LEGACY_ACCOUNT";

    private ResourceType() {
    }
}