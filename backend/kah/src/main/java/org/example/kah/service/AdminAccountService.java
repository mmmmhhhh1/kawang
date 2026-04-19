package org.example.kah.service;

import java.util.List;
import org.example.kah.common.CursorPageResponse;
import org.example.kah.dto.admin.AdminAccountCreateRequest;
import org.example.kah.dto.admin.AdminAccountDetailView;
import org.example.kah.dto.admin.AdminAccountView;

public interface AdminAccountService {

    CursorPageResponse<AdminAccountView> page(
            int size,
            String cursor,
            Long productId,
            String saleStatus,
            String enableStatus,
            String usedStatus,
            String keyword);

    AdminAccountDetailView detail(Long id);

    List<AdminAccountView> create(AdminAccountCreateRequest request);

    AdminAccountView updateStatus(Long id, String enableStatus);

    AdminAccountView updateUsedStatus(Long id, String usedStatus);

    int bulkDisable(String scope, Long productId);

    int bulkEnable(String scope, Long productId);

    void delete(Long id);
}