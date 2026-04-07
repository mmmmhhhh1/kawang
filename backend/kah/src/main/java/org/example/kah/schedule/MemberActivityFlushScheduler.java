package org.example.kah.schedule;

import lombok.RequiredArgsConstructor;
import org.example.kah.service.MemberActivityCacheService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 定期将 Redis 中的会员活跃时间回写数据库。
 */
@Component
@RequiredArgsConstructor
public class MemberActivityFlushScheduler {

    private final MemberActivityCacheService memberActivityCacheService;

    @Scheduled(fixedDelay = 300000)
    public void flushActivities() {
        memberActivityCacheService.flushDirtyActivities();
    }
}