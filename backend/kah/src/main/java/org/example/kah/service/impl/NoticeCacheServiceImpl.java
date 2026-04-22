package org.example.kah.service.impl;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.example.kah.cache.NoticeCacheCodec;
import org.example.kah.cache.NoticeCacheConstants;
import org.example.kah.dto.publicapi.NoticeView;
import org.example.kah.mapper.NoticeMapper;
import org.example.kah.metrics.ShopMetricsService;
import org.example.kah.service.NoticeCacheService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class NoticeCacheServiceImpl implements NoticeCacheService {

    private static final Logger log = LoggerFactory.getLogger(NoticeCacheServiceImpl.class);
    private static final String PUBLISHED_NOTICE_LOCAL_KEY = "published";

    private final StringRedisTemplate stringRedisTemplate;
    private final NoticeCacheCodec noticeCacheCodec;
    private final NoticeMapper noticeMapper;
    private final ShopMetricsService shopMetricsService;
    private final Cache<String, List<NoticeView>> publishedNoticeLocalCache = Caffeine.newBuilder()
            .maximumSize(1)
            .expireAfterWrite(NoticeCacheConstants.LOCAL_CACHE_TTL)
            .build();

    @Override
    public List<NoticeView> getPublishedNotices() {
        List<NoticeView> localCached = publishedNoticeLocalCache.getIfPresent(PUBLISHED_NOTICE_LOCAL_KEY);
        if (localCached != null) {
            shopMetricsService.recordNoticeCacheHit();
            return localCached;
        }

        try {
            String cached = stringRedisTemplate.opsForValue().get(NoticeCacheConstants.PUBLISHED_NOTICE_LIST_KEY);
            if (cached != null) {
                shopMetricsService.recordNoticeCacheHit();
                List<NoticeView> notices = noticeCacheCodec.parsePublishedList(cached);
                publishedNoticeLocalCache.put(PUBLISHED_NOTICE_LOCAL_KEY, notices);
                return notices;
            }

            shopMetricsService.recordNoticeCacheMiss();
            List<NoticeView> notices = loadPublishedNoticesFromDb();
            writePublishedCache(notices);
            shopMetricsService.recordNoticeCacheRebuild();
            return notices;
        } catch (Exception exception) {
            shopMetricsService.recordNoticeCacheFallback();
            log.warn("Failed to read published notices from cache, falling back to database", exception);
            List<NoticeView> notices = loadPublishedNoticesFromDb();
            publishedNoticeLocalCache.put(PUBLISHED_NOTICE_LOCAL_KEY, notices);
            return notices;
        }
    }

    @Override
    public void refreshPublishedNotices() {
        try {
            writePublishedCache(loadPublishedNoticesFromDb());
        } catch (Exception exception) {
            log.warn("Failed to refresh published notice cache", exception);
            evictPublishedNotices();
        }
    }

    @Override
    public void evictPublishedNotices() {
        try {
            stringRedisTemplate.delete(NoticeCacheConstants.PUBLISHED_NOTICE_LIST_KEY);
            publishedNoticeLocalCache.invalidate(PUBLISHED_NOTICE_LOCAL_KEY);
        } catch (Exception exception) {
            log.warn("Failed to delete published notice cache", exception);
        }
    }

    private List<NoticeView> loadPublishedNoticesFromDb() {
        return noticeMapper.findPublished().stream()
                .map(notice -> new NoticeView(
                        notice.getId(),
                        notice.getTitle(),
                        notice.getSummary(),
                        notice.getContent(),
                        notice.getPublishedAt()))
                .toList();
    }

    private void writePublishedCache(List<NoticeView> notices) {
        stringRedisTemplate.opsForValue().set(NoticeCacheConstants.PUBLISHED_NOTICE_LIST_KEY, noticeCacheCodec.toJson(notices));
        publishedNoticeLocalCache.put(PUBLISHED_NOTICE_LOCAL_KEY, notices);
    }
}
