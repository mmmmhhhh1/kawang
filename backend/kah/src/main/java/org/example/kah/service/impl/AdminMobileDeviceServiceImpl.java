package org.example.kah.service.impl;

import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.example.kah.common.ErrorCode;
import org.example.kah.entity.AdminMobileDevice;
import org.example.kah.entity.AdminStatus;
import org.example.kah.entity.AdminUser;
import org.example.kah.mapper.AdminMobileDeviceMapper;
import org.example.kah.mapper.AdminUserMapper;
import org.example.kah.security.AuthenticatedUser;
import org.example.kah.security.UserScope;
import org.example.kah.service.AdminMobileDeviceService;
import org.example.kah.service.impl.base.AbstractServiceSupport;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminMobileDeviceServiceImpl extends AbstractServiceSupport implements AdminMobileDeviceService {

    private final AdminUserMapper adminUserMapper;
    private final AdminMobileDeviceMapper adminMobileDeviceMapper;

    @Override
    @Transactional
    public void register(AuthenticatedUser currentUser, String vendor, String deviceToken, String deviceName) {
        AdminUser adminUser = requireActiveAdmin(currentUser);
        AdminMobileDevice device = new AdminMobileDevice();
        device.setAdminUserId(adminUser.getId());
        device.setVendor(normalizeVendor(vendor));
        device.setDeviceToken(normalizeToken(deviceToken));
        device.setDeviceName(normalizeDeviceName(deviceName));
        device.setPushEnabled(Boolean.TRUE);
        device.setLastSeenAt(LocalDateTime.now());
        adminMobileDeviceMapper.upsert(device);
    }

    @Override
    @Transactional
    public void unregister(AuthenticatedUser currentUser, String vendor, String deviceToken) {
        AdminUser adminUser = requireActiveAdmin(currentUser);
        adminMobileDeviceMapper.deleteByAdminAndToken(adminUser.getId(), normalizeVendor(vendor), normalizeToken(deviceToken));
    }

    private AdminUser requireActiveAdmin(AuthenticatedUser currentUser) {
        require(currentUser != null && UserScope.ADMIN.equals(currentUser.scope()), ErrorCode.UNAUTHORIZED, "管理员登录状态已失效");
        AdminUser adminUser = adminUserMapper.findById(currentUser.userId());
        require(adminUser != null, ErrorCode.UNAUTHORIZED, "管理员登录状态已失效");
        require(AdminStatus.ACTIVE.equals(adminUser.getStatus()), ErrorCode.FORBIDDEN, "当前管理员已被禁用");
        return adminUser;
    }

    private String normalizeVendor(String vendor) {
        String value = trim(vendor);
        require(value != null && !value.isBlank(), "设备厂商不能为空");
        require(value.length() <= 32, "设备厂商格式不正确");
        return value.toUpperCase();
    }

    private String normalizeToken(String deviceToken) {
        String value = trim(deviceToken);
        require(value != null && !value.isBlank(), "设备令牌不能为空");
        require(value.length() <= 512, "设备令牌过长");
        return value;
    }

    private String normalizeDeviceName(String deviceName) {
        String value = trim(deviceName);
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.length() > 120 ? value.substring(0, 120) : value;
    }
}
