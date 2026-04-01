package org.example.kah.service.impl;

import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.example.kah.common.BusinessException;
import org.example.kah.common.ErrorCode;
import org.example.kah.dto.admin.AdminNoticeSaveRequest;
import org.example.kah.dto.admin.AdminNoticeView;
import org.example.kah.dto.publicapi.NoticeView;
import org.example.kah.entity.NoticeStatus;
import org.example.kah.entity.ShopNotice;
import org.example.kah.mapper.NoticeMapper;
import org.example.kah.service.NoticeService;
import org.example.kah.service.impl.base.AbstractCrudService;
import org.springframework.stereotype.Service;

/**
 * {@link NoticeService} 的默认实现。
 * 负责前后台公告读取以及后台公告维护。
 */
@Service
@RequiredArgsConstructor
public class NoticeServiceImpl extends AbstractCrudService<ShopNotice, Long> implements NoticeService {

    private final NoticeMapper noticeMapper;

    /**
     * 查询前台已发布公告。
     */
    @Override
    public List<NoticeView> listPublished() {
        return noticeMapper.findPublished().stream().map(this::toPublicView).toList();
    }

    /**
     * 查询后台公告列表。
     */
    @Override
    public List<AdminNoticeView> listAdmin() {
        return noticeMapper.findAll().stream().map(this::toAdminView).toList();
    }

    /**
     * 创建公告并在需要时写入发布时间。
     */
    @Override
    public AdminNoticeView create(AdminNoticeSaveRequest request) {
        ensureStatus(request.status());
        ShopNotice notice = new ShopNotice();
        fillNotice(notice, request);
        noticeMapper.insert(notice);
        return toAdminView(noticeMapper.findById(notice.getId()));
    }

    /**
     * 更新公告内容。
     */
    @Override
    public AdminNoticeView update(Long id, AdminNoticeSaveRequest request) {
        ensureStatus(request.status());
        ShopNotice notice = requireById(id);
        fillNotice(notice, request);
        noticeMapper.update(notice);
        return toAdminView(noticeMapper.findById(id));
    }

    /**
     * 切换公告状态。
     */
    @Override
    public AdminNoticeView updateStatus(Long id, String status) {
        ensureStatus(status);
        requireById(id);
        noticeMapper.updateStatus(id, status);
        return toAdminView(noticeMapper.findById(id));
    }

    /**
     * 按主键查询公告实体。
     */
    @Override
    protected ShopNotice findEntityById(Long id) {
        return noticeMapper.findById(id);
    }

    /**
     * 返回实体名称供异常提示复用。
     */
    @Override
    protected String entityLabel() {
        return "公告";
    }

    /**
     * 将请求字段回填到公告实体。
     */
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

    /**
     * 校验公告状态是否合法。
     */
    private void ensureStatus(String status) {
        if (!NoticeStatus.PUBLISHED.equals(status) && !NoticeStatus.HIDDEN.equals(status)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "公告状态非法");
        }
    }

    /**
     * 映射前台公告视图。
     */
    private NoticeView toPublicView(ShopNotice notice) {
        return new NoticeView(
                notice.getId(),
                notice.getTitle(),
                notice.getSummary(),
                notice.getContent(),
                notice.getPublishedAt());
    }

    /**
     * 映射后台公告视图。
     */
    private AdminNoticeView toAdminView(ShopNotice notice) {
        return new AdminNoticeView(
                notice.getId(),
                notice.getTitle(),
                notice.getSummary(),
                notice.getContent(),
                notice.getStatus(),
                notice.getSortOrder(),
                notice.getPublishedAt(),
                notice.getUpdatedAt());
    }
}
