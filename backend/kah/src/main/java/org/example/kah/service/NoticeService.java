package org.example.kah.service;

import java.util.List;
import org.example.kah.common.CursorPageResponse;
import org.example.kah.dto.admin.AdminNoticeSaveRequest;
import org.example.kah.dto.admin.AdminNoticeView;
import org.example.kah.dto.publicapi.NoticeView;

public interface NoticeService {

    List<NoticeView> listPublished();

    CursorPageResponse<AdminNoticeView> pageAdmin(int size, String cursor, String keyword, String status);

    AdminNoticeView create(AdminNoticeSaveRequest request);

    AdminNoticeView update(Long id, AdminNoticeSaveRequest request);

    AdminNoticeView updateStatus(Long id, String status);

    void delete(Long id);
}
