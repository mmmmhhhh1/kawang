package org.example.kah.service.impl;

import java.time.Duration;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.example.kah.cache.NoticeCacheCodec;
import org.example.kah.cache.NoticeCacheConstants;
import org.example.kah.dto.publicapi.NoticeView;
import org.example.kah.mapper.NoticeMapper;
import org.example.kah.metrics.ShopMetricsService;
import org.example.kah.service.NoticeCacheService;
import org.example.kah.util.CacheTtlUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class NoticeCacheServiceImpl implements NoticeCacheService {

    private static final Logger log = LoggerFactory.getLogger(NoticeCacheServiceImpl.class);

    private final StringRedisTemplate stringRedisTemplate;
    private final NoticeCacheCodec noticeCacheCodec;
    private final NoticeMapper noticeMapper;
    private final ShopMetricsService shopMetricsService;

    @Override
    public List<NoticeView> getPublishedNotices() {
        try {
            String cached = stringRedisTemplate.opsForValue().get(NoticeCacheConstants.PUBLISHED_NOTICE_LIST_KEY);
            if (cached != null) {
                shopMetricsService.recordNoticeCacheHit();
                return noticeCacheCodec.parsePublishedList(cached);
            }
            shopMetricsService.recordNoticeCacheMiss();
            List<NoticeView> notices = loadPublishedNoticesFromDb();
            writePublishedCache(notices);
            shopMetricsService.recordNoticeCacheRebuild();
            return notices;
        } catch (Exception exception) {
            shopMetricsService.recordNoticeCacheFallback();
            log.warn("读取前台公告缓存失败，回退数据库查询", exception);
            return loadPublishedNoticesFromDb();
        }
    }

    @Override
    public void refreshPublishedNotices() {
        try {
            writePublishedCache(loadPublishedNoticesFromDb());
        } catch (Exception exception) {
            log.warn("刷新前台公告缓存失败", exception);
            evictPublishedNotices();
        }
    }

    @Override
    public void evictPublishedNotices() {
        try {
            stringRedisTemplate.delete(NoticeCacheConstants.PUBLISHED_NOTICE_LIST_KEY);
        } catch (Exception exception) {
            log.warn("删除前台公告缓存失败", exception);
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
        stringRedisTemplate.opsForValue().set(
                NoticeCacheConstants.PUBLISHED_NOTICE_LIST_KEY,
                noticeCacheCodec.toJson(notices),
                publishedNoticeTtl());
    }

    private Duration publishedNoticeTtl() {
        return CacheTtlUtils.withJitter(NoticeCacheConstants.PUBLISHED_NOTICE_TTL, NoticeCacheConstants.PUBLISHED_NOTICE_JITTER);
    }
}