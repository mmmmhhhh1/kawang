package org.example.kah.service.impl;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.example.kah.common.BusinessException;
import org.example.kah.common.CursorPageResponse;
import org.example.kah.common.ErrorCode;
import org.example.kah.dto.admin.AdminUserCreateRequest;
import org.example.kah.dto.admin.AdminUserDetailView;
import org.example.kah.dto.admin.AdminUserItemView;
import org.example.kah.entity.AdminPermissionCode;
import org.example.kah.entity.AdminStatus;
import org.example.kah.entity.AdminUser;
import org.example.kah.mapper.AdminUserMapper;
import org.example.kah.mapper.AdminUserPermissionMapper;
import org.example.kah.service.AdminManagerService;
import org.example.kah.service.impl.base.AbstractCrudService;
import org.example.kah.util.CursorCodecUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminManagerServiceImpl extends AbstractCrudService<AdminUser, Long> implements AdminManagerService {

    private final AdminUserMapper adminUserMapper;
    private final AdminUserPermissionMapper adminUserPermissionMapper;
    private final PasswordEncoder passwordEncoder;

    @Override
    public List<AdminUserItemView> list() {
        return adminUserMapper.findAll().stream().map(this::toItemView).toList();
    }

    @Override
    public CursorPageResponse<AdminUserItemView> page(int size, String cursor, String keyword) {
        int safeSize = normalizeSize(size, 50);
        CursorCodecUtils.DecodedCursor decodedCursor = CursorCodecUtils.decode(cursor);
        Map<String, Object> params = new HashMap<>();
        params.put("keyword", trim(keyword));
        params.put("limit", safeSize + 1);
        if (decodedCursor != null) {
            params.put("cursorCreatedAt", decodedCursor.createdAt());
            params.put("cursorId", decodedCursor.id());
        }
        List<AdminUser> rows = adminUserMapper.findAdminCursorPage(params);
        boolean hasMore = rows.size() > safeSize;
        List<AdminUser> pageItems = hasMore ? rows.subList(0, safeSize) : rows;
        String nextCursor = hasMore
                ? CursorCodecUtils.encode(pageItems.get(pageItems.size() - 1).getCreatedAt(), pageItems.get(pageItems.size() - 1).getId())
                : null;
        return new CursorPageResponse<>(pageItems.stream().map(this::toItemView).toList(), nextCursor, hasMore);
    }

    @Override
    @Transactional
    public AdminUserDetailView create(AdminUserCreateRequest request) {
        if (adminUserMapper.findByUsername(trim(request.username())) != null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "管理员用户名已存在");
        }

        AdminUser adminUser = new AdminUser();
        adminUser.setUsername(trim(request.username()));
        adminUser.setDisplayName(trim(request.displayName()));
        adminUser.setPasswordHash(passwordEncoder.encode(request.password()));
        adminUser.setStatus(AdminStatus.ACTIVE);
        adminUser.setIsSuperAdmin(false);
        try {
            adminUserMapper.insert(adminUser);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "管理员用户名已存在");
        }

        replacePermissions(adminUser.getId(), request.permissions());
        return toDetailView(requireById(adminUser.getId()));
    }

    @Override
    @Transactional
    public AdminUserDetailView updatePermissions(Long id, List<String> permissions) {
        AdminUser adminUser = requireById(id);
        require(!Boolean.TRUE.equals(adminUser.getIsSuperAdmin()), ErrorCode.BAD_REQUEST, "超级管理员不能修改权限");
        replacePermissions(id, permissions);
        return toDetailView(requireById(id));
    }

    @Override
    @Transactional
    public void delete(Long id) {
        AdminUser adminUser = requireById(id);
        require(!Boolean.TRUE.equals(adminUser.getIsSuperAdmin()), ErrorCode.BAD_REQUEST, "超级管理员不能删除");
        adminUserPermissionMapper.deleteByAdminUserId(id);
        adminUserMapper.deleteById(id);
    }

    @Override
    protected AdminUser findEntityById(Long id) {
        return adminUserMapper.findById(id);
    }

    @Override
    protected String entityLabel() {
        return "管理员";
    }

    private void replacePermissions(Long adminUserId, List<String> permissions) {
        List<String> normalized = normalizePermissions(permissions);
        adminUserPermissionMapper.deleteByAdminUserId(adminUserId);
        if (!normalized.isEmpty()) {
            adminUserPermissionMapper.insertBatch(adminUserId, normalized);
        }
    }

    private List<String> normalizePermissions(List<String> permissions) {
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        if (permissions == null) {
            return List.of();
        }
        for (String permission : permissions) {
            String value = trim(permission);
            if (value == null || value.isBlank()) {
                continue;
            }
            if (!AdminPermissionCode.isSupported(value)) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "存在不支持的管理员权限");
            }
            normalized.add(value);
        }
        return List.copyOf(normalized);
    }

    private List<String> resolvePermissions(AdminUser adminUser) {
        if (Boolean.TRUE.equals(adminUser.getIsSuperAdmin())) {
            return AdminPermissionCode.all();
        }
        return adminUserPermissionMapper.findByAdminUserId(adminUser.getId()).stream()
                .map(item -> item.getPermissionCode())
                .toList();
    }

    private AdminUserItemView toItemView(AdminUser adminUser) {
        return new AdminUserItemView(
                adminUser.getId(),
                adminUser.getUsername(),
                adminUser.getDisplayName(),
                Boolean.TRUE.equals(adminUser.getIsSuperAdmin()),
                resolvePermissions(adminUser),
                adminUser.getCreatedAt());
    }

    private AdminUserDetailView toDetailView(AdminUser adminUser) {
        return new AdminUserDetailView(
                adminUser.getId(),
                adminUser.getUsername(),
                adminUser.getDisplayName(),
                Boolean.TRUE.equals(adminUser.getIsSuperAdmin()),
                resolvePermissions(adminUser),
                adminUser.getCreatedAt(),
                adminUser.getUpdatedAt());
    }
}