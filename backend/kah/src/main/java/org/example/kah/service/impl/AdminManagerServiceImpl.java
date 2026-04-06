package org.example.kah.service.impl;

import java.util.LinkedHashSet;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.example.kah.common.BusinessException;
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
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@link AdminManagerService} 默认实现。
 * 负责普通管理员的创建、授权和删除，最高权限管理员只允许查看。
 */
@Service
@RequiredArgsConstructor
public class AdminManagerServiceImpl extends AbstractCrudService<AdminUser, Long> implements AdminManagerService {

    private final AdminUserMapper adminUserMapper;
    private final AdminUserPermissionMapper adminUserPermissionMapper;
    private final PasswordEncoder passwordEncoder;

    /**
     * 查询管理员列表。
     */
    @Override
    public List<AdminUserItemView> list() {
        return adminUserMapper.findAll().stream().map(this::toItemView).toList();
    }

    /**
     * 创建普通管理员并写入权限集。
     */
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

    /**
     * 更新普通管理员权限。
     */
    @Override
    @Transactional
    public AdminUserDetailView updatePermissions(Long id, List<String> permissions) {
        AdminUser adminUser = requireById(id);
        require(!Boolean.TRUE.equals(adminUser.getIsSuperAdmin()), ErrorCode.BAD_REQUEST, "最高权限管理员不能修改权限");
        replacePermissions(id, permissions);
        return toDetailView(requireById(id));
    }

    /**
     * 删除普通管理员及其权限关系。
     */
    @Override
    @Transactional
    public void delete(Long id) {
        AdminUser adminUser = requireById(id);
        require(!Boolean.TRUE.equals(adminUser.getIsSuperAdmin()), ErrorCode.BAD_REQUEST, "最高权限管理员不能删除");
        adminUserPermissionMapper.deleteByAdminUserId(id);
        adminUserMapper.deleteById(id);
    }

    /**
     * 按主键查询管理员实体。
     */
    @Override
    protected AdminUser findEntityById(Long id) {
        return adminUserMapper.findById(id);
    }

    /**
     * 统一管理员实体名称，复用抽象基类的未找到提示。
     */
    @Override
    protected String entityLabel() {
        return "管理员";
    }

    /**
     * 重建某个管理员的权限列表。
     */
    private void replacePermissions(Long adminUserId, List<String> permissions) {
        List<String> normalized = normalizePermissions(permissions);
        adminUserPermissionMapper.deleteByAdminUserId(adminUserId);
        if (!normalized.isEmpty()) {
            adminUserPermissionMapper.insertBatch(adminUserId, normalized);
        }
    }

    /**
     * 归一化权限列表并校验合法性。
     */
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

    /**
     * 读取某个管理员的生效权限列表。
     */
    private List<String> resolvePermissions(AdminUser adminUser) {
        if (Boolean.TRUE.equals(adminUser.getIsSuperAdmin())) {
            return AdminPermissionCode.all();
        }
        return adminUserPermissionMapper.findByAdminUserId(adminUser.getId()).stream()
                .map(item -> item.getPermissionCode())
                .toList();
    }

    /**
     * 将管理员实体映射为列表视图。
     */
    private AdminUserItemView toItemView(AdminUser adminUser) {
        return new AdminUserItemView(
                adminUser.getId(),
                adminUser.getUsername(),
                adminUser.getDisplayName(),
                Boolean.TRUE.equals(adminUser.getIsSuperAdmin()),
                resolvePermissions(adminUser),
                adminUser.getCreatedAt());
    }

    /**
     * 将管理员实体映射为详情视图。
     */
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