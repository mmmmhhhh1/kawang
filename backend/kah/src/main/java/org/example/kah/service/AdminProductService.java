package org.example.kah.service;

import java.util.List;
import org.example.kah.common.CursorPageResponse;
import org.example.kah.dto.admin.AdminProductSaveRequest;
import org.example.kah.dto.admin.AdminProductView;

public interface AdminProductService {

    List<AdminProductView> list();

    CursorPageResponse<AdminProductView> page(int size, String cursor, String keyword, String status);

    AdminProductView create(AdminProductSaveRequest request);

    AdminProductView update(Long id, AdminProductSaveRequest request);

    AdminProductView updateStatus(Long id, String status);

    void delete(Long id);
}