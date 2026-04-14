package org.example.kah.service;

import org.example.kah.dto.admin.AdminProfileResponse;
import org.example.kah.entity.AdminUser;
import org.example.kah.security.AuthenticatedUser;

public interface AdminPermissionService {

    AdminProfileResponse buildProfile(AdminUser adminUser);

    void requirePermission(AuthenticatedUser currentUser, String permission);

    void requireSuperAdmin(AuthenticatedUser currentUser);
}