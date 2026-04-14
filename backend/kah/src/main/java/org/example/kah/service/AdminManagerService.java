package org.example.kah.service;

import java.util.List;
import org.example.kah.common.CursorPageResponse;
import org.example.kah.dto.admin.AdminUserCreateRequest;
import org.example.kah.dto.admin.AdminUserDetailView;
import org.example.kah.dto.admin.AdminUserItemView;

public interface AdminManagerService {

    List<AdminUserItemView> list();

    CursorPageResponse<AdminUserItemView> page(int size, String cursor, String keyword);

    AdminUserDetailView create(AdminUserCreateRequest request);

    AdminUserDetailView updatePermissions(Long id, List<String> permissions);

    void delete(Long id);
}