package org.example.kah.service;

import java.time.LocalDateTime;
import java.util.List;
import org.example.kah.dto.admin.AdminMemberActivityView;

/**
 * 会员活跃时间缓存服务。
 * 负责将实时活跃时间写入 Redis，并对后台查询提供聚合读取能力。
 */
public interface MemberActivityCacheService {

    /** 记录会员最近活跃时间。 */
    void recordSeen(Long userId);

    /** 记录会员最近登录时间，同时刷新最近活跃时间。 */
    void recordLogin(Long userId, LocalDateTime loginAt);

    /** 批量读取会员活动信息，Redis 缺失时回退数据库。 */
    List<AdminMemberActivityView> getActivities(List<Long> userIds);

    /** 读取单个会员的活动信息，Redis 缺失时回退数据库。 */
    AdminMemberActivityView getActivity(Long userId);

    /** 将 Redis 中的脏活动时间回写数据库。 */
    void flushDirtyActivities();
}