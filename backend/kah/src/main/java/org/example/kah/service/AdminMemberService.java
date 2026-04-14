package org.example.kah.service;

import java.util.List;
import org.example.kah.common.CursorPageResponse;
import org.example.kah.dto.admin.AdminMemberActivityView;
import org.example.kah.dto.admin.AdminMemberDetailView;
import org.example.kah.dto.admin.AdminMemberListView;

public interface AdminMemberService {

    List<AdminMemberListView> list();

    CursorPageResponse<AdminMemberListView> page(int size, String cursor, String keyword, String status);

    AdminMemberDetailView detail(Long id);

    List<AdminMemberActivityView> listActivities(List<Long> ids);

    AdminMemberActivityView activity(Long id);

    AdminMemberListView updateStatus(Long id, String status);
}