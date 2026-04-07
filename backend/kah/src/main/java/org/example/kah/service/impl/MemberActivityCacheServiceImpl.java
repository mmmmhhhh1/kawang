package org.example.kah.service.impl;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.example.kah.dto.admin.AdminMemberActivityView;
import org.example.kah.entity.MemberUser;
import org.example.kah.mapper.MemberUserMapper;
import org.example.kah.service.MemberActivityCacheService;
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

    private static final String MEMBER_ACTIVITY_KEY_PREFIX = "member:activity:";
    private static final String MEMBER_ACTIVITY_DIRTY_SET_KEY = "member:activity:dirty-users";
    private static final String FIELD_LAST_SEEN_AT = "lastSeenAt";
    private static final String FIELD_LAST_LOGIN_AT = "lastLoginAt";
    private static final String FIELD_DIRTY = "dirty";
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final StringRedisTemplate stringRedisTemplate;
    private final MemberUserMapper memberUserMapper;

    @Override
    public void recordSeen(Long userId) {
        if (userId == null) {
            return;
        }
        recordSeen(userId, LocalDateTime.now());
    }

    @Override
    public void recordLogin(Long userId, LocalDateTime loginAt) {
        if (userId == null || loginAt == null) {
            return;
        }
        HashOperations<String, Object, Object> hashOperations = stringRedisTemplate.opsForHash();
        String key = activityKey(userId);
        String value = format(loginAt);
        hashOperations.put(key, FIELD_LAST_LOGIN_AT, value);
        hashOperations.put(key, FIELD_LAST_SEEN_AT, value);
        hashOperations.put(key, FIELD_DIRTY, "1");
        stringRedisTemplate.opsForSet().add(MEMBER_ACTIVITY_DIRTY_SET_KEY, String.valueOf(userId));
    }

    @Override
    public List<AdminMemberActivityView> getActivities(List<Long> userIds) {
        List<Long> normalizedIds = normalizeIds(userIds);
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
                AdminMemberActivityView view = toActivityView(userId, memberUser == null ? null : memberUser.getLastSeenAt(), memberUser == null ? null : memberUser.getLastLoginAt());
                activities.put(userId, view);
                backfillCache(view);
            }
        }

        return normalizedIds.stream().map(activities::get).toList();
    }

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
        AdminMemberActivityView fallback = toActivityView(userId, memberUser == null ? null : memberUser.getLastSeenAt(), memberUser == null ? null : memberUser.getLastLoginAt());
        backfillCache(fallback);
        return fallback;
    }

    @Override
    public void flushDirtyActivities() {
        Set<String> dirtyMembers = stringRedisTemplate.opsForSet().members(MEMBER_ACTIVITY_DIRTY_SET_KEY);
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
            String key = activityKey(userId);
            Object dirty = hashOperations.get(key, FIELD_DIRTY);
            if (!"1".equals(String.valueOf(dirty))) {
                flushedIds.add(memberIdText);
                continue;
            }
            LocalDateTime lastSeenAt = parseDateTime(hashOperations.get(key, FIELD_LAST_SEEN_AT));
            LocalDateTime lastLoginAt = parseDateTime(hashOperations.get(key, FIELD_LAST_LOGIN_AT));
            memberUserMapper.mergeActivityState(userId, lastLoginAt, lastSeenAt);
            hashOperations.put(key, FIELD_DIRTY, "0");
            flushedIds.add(memberIdText);
        }

        if (!flushedIds.isEmpty()) {
            stringRedisTemplate.opsForSet().remove(MEMBER_ACTIVITY_DIRTY_SET_KEY, flushedIds.toArray());
        }
    }

    private void recordSeen(Long userId, LocalDateTime seenAt) {
        HashOperations<String, Object, Object> hashOperations = stringRedisTemplate.opsForHash();
        String key = activityKey(userId);
        hashOperations.put(key, FIELD_LAST_SEEN_AT, format(seenAt));
        hashOperations.put(key, FIELD_DIRTY, "1");
        stringRedisTemplate.opsForSet().add(MEMBER_ACTIVITY_DIRTY_SET_KEY, String.valueOf(userId));
    }

    private void backfillCache(AdminMemberActivityView view) {
        if (view == null || view.userId() == null) {
            return;
        }
        HashOperations<String, Object, Object> hashOperations = stringRedisTemplate.opsForHash();
        String key = activityKey(view.userId());
        if (view.lastSeenAt() != null) {
            hashOperations.put(key, FIELD_LAST_SEEN_AT, format(view.lastSeenAt()));
        }
        if (view.lastLoginAt() != null) {
            hashOperations.put(key, FIELD_LAST_LOGIN_AT, format(view.lastLoginAt()));
        }
        hashOperations.put(key, FIELD_DIRTY, "0");
    }

    private AdminMemberActivityView readFromRedis(Long userId) {
        Map<Object, Object> values = stringRedisTemplate.opsForHash().entries(activityKey(userId));
        if (values == null || values.isEmpty()) {
            return null;
        }
        LocalDateTime lastSeenAt = parseDateTime(values.get(FIELD_LAST_SEEN_AT));
        LocalDateTime lastLoginAt = parseDateTime(values.get(FIELD_LAST_LOGIN_AT));
        if (lastSeenAt == null && lastLoginAt == null) {
            return null;
        }
        return toActivityView(userId, lastSeenAt, lastLoginAt);
    }

    private AdminMemberActivityView toActivityView(Long userId, LocalDateTime lastSeenAt, LocalDateTime lastLoginAt) {
        return new AdminMemberActivityView(userId, lastSeenAt, lastLoginAt);
    }

    private List<Long> normalizeIds(List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return List.of();
        }
        return new ArrayList<>(new LinkedHashSet<>(userIds.stream().filter(java.util.Objects::nonNull).toList()));
    }

    private String activityKey(Long userId) {
        return MEMBER_ACTIVITY_KEY_PREFIX + userId;
    }

    private String format(LocalDateTime value) {
        return value.format(FORMATTER);
    }

    private LocalDateTime parseDateTime(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return LocalDateTime.parse(String.valueOf(value), FORMATTER);
        } catch (DateTimeParseException exception) {
            return null;
        }
    }

    private Long parseLong(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException exception) {
            return null;
        }
    }
}