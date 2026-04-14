package org.example.kah.service;

import org.example.kah.common.CursorPageResponse;
import org.example.kah.dto.admin.AdminRechargeDetailView;
import org.example.kah.dto.admin.AdminRechargeItemView;
import org.example.kah.security.AuthenticatedUser;
import org.springframework.core.io.Resource;

public interface AdminRechargeService {

    CursorPageResponse<AdminRechargeItemView> list(int size, String cursor, String status, String userKeyword);

    AdminRechargeDetailView detail(Long id);

    Resource loadScreenshot(Long id);

    AdminRechargeDetailView approve(Long id, AuthenticatedUser currentUser);

    AdminRechargeDetailView reject(Long id, String reason, AuthenticatedUser currentUser);
}