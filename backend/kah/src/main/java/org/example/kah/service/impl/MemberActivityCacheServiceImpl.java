package org.example.kah.service.impl;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.example.kah.cache.MemberActivityCacheConstants;
import org.example.kah.dto.admin.AdminMemberActivityView;
import org.example.kah.entity.MemberUser;
import org.example.kah.mapper.MemberUserMapper;
import org.example.kah.service.MemberActivityCacheService;
import org.example.kah.util.LocalDateTimeCodecUtils;
import org.example.kah.util.LongIdUtils;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * {@link MemberActivityCacheService} 的默认实现。
 * 使用 Redis 维护实时活跃时间，并周期性回写到数据库。
 */
@Service
@RequiredArgsConstructor
public class MemberActivityCacheServiceImpl implements MemberActivityCacheService {

    private final StringRedisTemplate stringRedisTemplate;
    private final MemberUserMapper memberUserMapper;

    /** 记录会员最近活跃时间。 */
    @Override
    public void recordSeen(Long userId) {
        if (userId == null) {
            return;
        }
        writeSeen(userId, LocalDateTime.now());
    }

    /** 记录会员最近登录时间，并同步刷新最近活跃时间。 */
    @Override
    public void recordLogin(Long userId, LocalDateTime loginAt) {
        if (userId == null || loginAt == null) {
            return;
        }
        HashOperations<String, Object, Object> hashOperations = stringRedisTemplate.opsForHash();
        String key = MemberActivityCacheConstants.activityKey(userId);
        String value = LocalDateTimeCodecUtils.format(loginAt);
        hashOperations.put(key, MemberActivityCacheConstants.FIELD_LAST_LOGIN_AT, value);
        hashOperations.put(key, MemberActivityCacheConstants.FIELD_LAST_SEEN_AT, value);
        markDirty(hashOperations, key, userId);
    }

    /** 批量读取会员活跃信息，Redis 未命中时回退数据库并回填缓存。 */
    @Override
    public List<AdminMemberActivityView> getActivities(List<Long> userIds) {
        List<Long> normalizedIds = LongIdUtils.normalizeDistinctIds(userIds);
        if (normalizedIds.isEmpty()) {
            return List.of();
        }

        Map<Long, AdminMemberActivityView> activities = new LinkedHashMap<>();
        List<Long> missingIds = new ArrayList<>();
        for (Long userId : normalizedIds) {
            AdminMemberActivityView cached = readFromRedis(userId);
            if (cached == null) {
                missingIds.add(userId);
            } else {
                activities.put(userId, cached);
            }
        }

        if (!missingIds.isEmpty()) {
            List<MemberUser> fallbackUsers = memberUserMapper.findByIds(missingIds);
            Map<Long, MemberUser> fallbackMap = fallbackUsers.stream()
                    .collect(Collectors.toMap(MemberUser::getId, item -> item));
            for (Long userId : missingIds) {
                MemberUser memberUser = fallbackMap.get(userId);
                AdminMemberActivityView view = toActivityView(
                        userId,
                        memberUser == null ? null : memberUser.getLastSeenAt(),
                        memberUser == null ? null : memberUser.getLastLoginAt());
                activities.put(userId, view);
                backfillCache(view);
            }
        }

        return normalizedIds.stream().map(activities::get).toList();
    }

    /** 读取单会员活跃信息，Redis 未命中时回退数据库并回填缓存。 */
    @Override
    public AdminMemberActivityView getActivity(Long userId) {
        if (userId == null) {
            return null;
        }
        AdminMemberActivityView cached = readFromRedis(userId);
        if (cached != null) {
            return cached;
        }
        MemberUser memberUser = memberUserMapper.findById(userId);
        AdminMemberActivityView fallback = toActivityView(
                userId,
                memberUser == null ? null : memberUser.getLastSeenAt(),
                memberUser == null ? null : memberUser.getLastLoginAt());
        backfillCache(fallback);
        return fallback;
    }

    /** 将 Redis 中标记为 dirty 的活跃记录回写数据库。 */
    @Override
    public void flushDirtyActivities() {
        Set<String> dirtyMembers = stringRedisTemplate.opsForSet().members(MemberActivityCacheConstants.MEMBER_ACTIVITY_DIRTY_SET_KEY);
        if (dirtyMembers == null || dirtyMembers.isEmpty()) {
            return;
        }

        List<String> flushedIds = new ArrayList<>();
        HashOperations<String, Object, Object> hashOperations = stringRedisTemplate.opsForHash();
        for (String memberIdText : dirtyMembers) {
            Long userId = parseLong(memberIdText);
            if (userId == null) {
                flushedIds.add(memberIdText);
                continue;
            }
            String key = MemberActivityCacheConstants.activityKey(userId);
            Object dirty = hashOperations.get(key, MemberActivityCacheConstants.FIELD_DIRTY);
            if (!"1".equals(String.valueOf(dirty))) {
                flushedIds.add(memberIdText);
                continue;
            }
            LocalDateTime lastSeenAt = LocalDateTimeCodecUtils.parse(hashOperations.get(key, MemberActivityCacheConstants.FIELD_LAST_SEEN_AT));
            LocalDateTime lastLoginAt = LocalDateTimeCodecUtils.parse(hashOperations.get(key, MemberActivityCacheConstants.FIELD_LAST_LOGIN_AT));
            memberUserMapper.mergeActivityState(userId, lastLoginAt, lastSeenAt);
            hashOperations.put(key, MemberActivityCacheConstants.FIELD_DIRTY, "0");
            flushedIds.add(memberIdText);
        }

        if (!flushedIds.isEmpty()) {
            stringRedisTemplate.opsForSet().remove(MemberActivityCacheConstants.MEMBER_ACTIVITY_DIRTY_SET_KEY, flushedIds.toArray());
        }
    }

    /** 写入最近活跃时间，并标记该会员待回写数据库。 */
    private void writeSeen(Long userId, LocalDateTime seenAt) {
        HashOperations<String, Object, Object> hashOperations = stringRedisTemplate.opsForHash();
        String key = MemberActivityCacheConstants.activityKey(userId);
        hashOperations.put(key, MemberActivityCacheConstants.FIELD_LAST_SEEN_AT, LocalDateTimeCodecUtils.format(seenAt));
        markDirty(hashOperations, key, userId);
    }

    /** 将数据库 fallback 结果回填到 Redis，减少下一次重复查库。 */
    private void backfillCache(AdminMemberActivityView view) {
        if (view == null || view.userId() == null) {
            return;
        }
        HashOperations<String, Object, Object> hashOperations = stringRedisTemplate.opsForHash();
        String key = MemberActivityCacheConstants.activityKey(view.userId());
        if (view.lastSeenAt() != null) {
            hashOperations.put(key, MemberActivityCacheConstants.FIELD_LAST_SEEN_AT, LocalDateTimeCodecUtils.format(view.lastSeenAt()));
        }
        if (view.lastLoginAt() != null) {
            hashOperations.put(key, MemberActivityCacheConstants.FIELD_LAST_LOGIN_AT, LocalDateTimeCodecUtils.format(view.lastLoginAt()));
        }
        hashOperations.put(key, MemberActivityCacheConstants.FIELD_DIRTY, "0");
    }

    /** 从 Redis 解析单会员活跃信息。 */
    private AdminMemberActivityView readFromRedis(Long userId) {
        Map<Object, Object> values = stringRedisTemplate.opsForHash().entries(MemberActivityCacheConstants.activityKey(userId));
        if (values == null || values.isEmpty()) {
            return null;
        }
        LocalDateTime lastSeenAt = LocalDateTimeCodecUtils.parse(values.get(MemberActivityCacheConstants.FIELD_LAST_SEEN_AT));
        LocalDateTime lastLoginAt = LocalDateTimeCodecUtils.parse(values.get(MemberActivityCacheConstants.FIELD_LAST_LOGIN_AT));
        if (lastSeenAt == null && lastLoginAt == null) {
            return null;
        }
        return toActivityView(userId, lastSeenAt, lastLoginAt);
    }

    /** 标记会员活跃缓存已变更，后续由定时任务回写数据库。 */
    private void markDirty(HashOperations<String, Object, Object> hashOperations, String key, Long userId) {
        hashOperations.put(key, MemberActivityCacheConstants.FIELD_DIRTY, "1");
        stringRedisTemplate.opsForSet().add(MemberActivityCacheConstants.MEMBER_ACTIVITY_DIRTY_SET_KEY, String.valueOf(userId));
    }

    /** 组装统一的后台会员活动视图。 */
    private AdminMemberActivityView toActivityView(Long userId, LocalDateTime lastSeenAt, LocalDateTime lastLoginAt) {
        return new AdminMemberActivityView(userId, lastSeenAt, lastLoginAt);
    }

    /** 安全解析字符串主键，非法值直接视为脏数据跳过。 */
    private Long parseLong(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException exception) {
            return null;
        }
    }
}
