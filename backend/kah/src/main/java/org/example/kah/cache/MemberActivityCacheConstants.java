package org.example.kah.cache;

/**
 * 会员活跃时间缓存常量。
 * 统一维护 Redis key 和 hash 字段名称，方便活跃时间相关逻辑复用。
 */
public final class MemberActivityCacheConstants {

    /** 单会员活跃信息缓存 key 前缀。 */
    public static final String MEMBER_ACTIVITY_KEY_PREFIX = "member:activity:";

    /** 待回写数据库的脏会员集合 key。 */
    public static final String MEMBER_ACTIVITY_DIRTY_SET_KEY = "member:activity:dirty-users";

    /** 最近活跃时间字段名。 */
    public static final String FIELD_LAST_SEEN_AT = "lastSeenAt";

    /** 最近登录时间字段名。 */
    public static final String FIELD_LAST_LOGIN_AT = "lastLoginAt";

    /** 脏标记字段名。 */
    public static final String FIELD_DIRTY = "dirty";

    private MemberActivityCacheConstants() {
    }

    /** 生成单会员活跃信息缓存 key。 */
    public static String activityKey(Long userId) {
        return MEMBER_ACTIVITY_KEY_PREFIX + userId;
    }
}
