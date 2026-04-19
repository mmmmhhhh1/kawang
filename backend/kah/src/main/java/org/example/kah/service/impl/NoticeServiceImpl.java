package org.example.kah.service.impl;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.example.kah.common.BusinessException;
import org.example.kah.common.CursorPageResponse;
import org.example.kah.common.ErrorCode;
import org.example.kah.dto.admin.AdminNoticeSaveRequest;
import org.example.kah.dto.admin.AdminNoticeView;
import org.example.kah.dto.publicapi.NoticeView;
import org.example.kah.entity.NoticeStatus;
import org.example.kah.entity.ShopNotice;
import org.example.kah.mapper.NoticeMapper;
import org.example.kah.service.NoticeCacheService;
import org.example.kah.service.NoticeService;
import org.example.kah.service.impl.base.AbstractCrudService;
import org.example.kah.util.CursorCodecUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
@RequiredArgsConstructor
public class NoticeServiceImpl extends AbstractCrudService<ShopNotice, Long> implements NoticeService {

    private final NoticeMapper noticeMapper;
    private final NoticeCacheService noticeCacheService;

    @Override
    public List<NoticeView> listPublished() {
        return noticeCacheService.getPublishedNotices();
    }

    @Override
    public CursorPageResponse<AdminNoticeView> pageAdmin(int size, String cursor, String keyword, String status) {
        int safeSize = normalizeSize(size, 50);
        CursorCodecUtils.DecodedCursor decodedCursor = CursorCodecUtils.decode(cursor);
        Map<String, Object> params = new HashMap<>();
        params.put("status", trim(status));
        params.put("keyword", trim(keyword));
        params.put("limit", safeSize + 1);
        if (decodedCursor != null) {
            params.put("cursorCreatedAt", decodedCursor.createdAt());
            params.put("cursorId", decodedCursor.id());
        }
        List<ShopNotice> rows = noticeMapper.findAdminCursorPage(params);
        boolean hasMore = rows.size() > safeSize;
        List<ShopNotice> pageItems = hasMore ? rows.subList(0, safeSize) : rows;
        String nextCursor = hasMore ? CursorCodecUtils.encode(pageItems.get(pageItems.size() - 1).getCreatedAt(), pageItems.get(pageItems.size() - 1).getId()) : null;
        return new CursorPageResponse<>(pageItems.stream().map(this::toAdminView).toList(), nextCursor, hasMore);
    }

    @Override
    @Transactional
    public AdminNoticeView create(AdminNoticeSaveRequest request) {
        ensureStatus(request.status());
        ShopNotice notice = new ShopNotice();
        fillNotice(notice, request);
        noticeMapper.insert(notice);
        refreshPublishedNoticeCacheAfterCommit();
        return toAdminView(noticeMapper.findById(notice.getId()));
    }

    @Override
    @Transactional
    public AdminNoticeView update(Long id, AdminNoticeSaveRequest request) {
        ensureStatus(request.status());
        ShopNotice notice = requireById(id);
        fillNotice(notice, request);
        noticeMapper.update(notice);
        refreshPublishedNoticeCacheAfterCommit();
        return toAdminView(noticeMapper.findById(id));
    }

    @Override
    @Transactional
    public AdminNoticeView updateStatus(Long id, String status) {
        ensureStatus(status);
        requireById(id);
        noticeMapper.updateStatus(id, status);
        refreshPublishedNoticeCacheAfterCommit();
        return toAdminView(noticeMapper.findById(id));
    }

    @Override
    protected ShopNotice findEntityById(Long id) {
        return noticeMapper.findById(id);
    }

    @Override
    protected String entityLabel() {
        return "公告";
    }

    private void fillNotice(ShopNotice notice, AdminNoticeSaveRequest request) {
        notice.setTitle(trim(request.title()));
        notice.setSummary(trim(request.summary()));
        notice.setContent(trim(request.content()));
        notice.setStatus(trim(request.status()));
        notice.setSortOrder(request.sortOrder());
        if (NoticeStatus.PUBLISHED.equals(request.status()) || notice.getPublishedAt() == null) {
            notice.setPublishedAt(LocalDateTime.now());
        }
    }

    private void ensureStatus(String status) {
        if (!NoticeStatus.PUBLISHED.equals(status) && !NoticeStatus.HIDDEN.equals(status)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "公告状态不合法");
        }
    }

    private AdminNoticeView toAdminView(ShopNotice notice) {
        return new AdminNoticeView(notice.getId(), notice.getTitle(), notice.getSummary(), notice.getContent(), notice.getStatus(), notice.getSortOrder(), notice.getPublishedAt(), notice.getUpdatedAt());
    }

    private void refreshPublishedNoticeCacheAfterCommit() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    noticeCacheService.refreshPublishedNotices();
                }
            });
            return;
        }
        noticeCacheService.refreshPublishedNotices();
    }
}