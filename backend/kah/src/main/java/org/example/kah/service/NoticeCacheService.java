package org.example.kah.service;

import java.util.List;
import org.example.kah.dto.publicapi.NoticeView;

public interface NoticeCacheService {

    List<NoticeView> getPublishedNotices();

    void refreshPublishedNotices();

    void evictPublishedNotices();
}
