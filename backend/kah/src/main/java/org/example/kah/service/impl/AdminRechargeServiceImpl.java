package org.example.kah.service.impl;

import java.util.HashMap;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.example.kah.common.BusinessException;
import org.example.kah.common.CursorPageResponse;
import org.example.kah.common.ErrorCode;
import org.example.kah.dto.admin.AdminRechargeDetailView;
import org.example.kah.dto.admin.AdminRechargeItemView;
import org.example.kah.entity.MemberRechargeRequest;
import org.example.kah.entity.MemberUser;
import org.example.kah.entity.RechargeStatus;
import org.example.kah.mapper.MemberRechargeRequestMapper;
import org.example.kah.security.AuthenticatedUser;
import org.example.kah.service.AdminPermissionService;
import org.example.kah.service.AdminRechargeService;
import org.example.kah.service.FileStorageService;
import org.example.kah.service.MemberBalanceService;
import org.example.kah.service.impl.base.AbstractServiceSupport;
import org.example.kah.util.CursorCodecUtils;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminRechargeServiceImpl extends AbstractServiceSupport implements AdminRechargeService {

    private final MemberRechargeRequestMapper memberRechargeRequestMapper;
    private final FileStorageService fileStorageService;
    private final AdminPermissionService adminPermissionService;
    private final MemberBalanceService memberBalanceService;

    @Override
    public CursorPageResponse<AdminRechargeItemView> list(int size, String cursor, String status, String userKeyword) {
        int safeSize = normalizeSize(size, 30);
        CursorCodecUtils.DecodedCursor decodedCursor = CursorCodecUtils.decode(cursor);
        HashMap<String, Object> params = new HashMap<>();
        params.put("status", trim(status));
        params.put("userKeyword", trim(userKeyword));
        params.put("limit", safeSize + 1);
        if (decodedCursor != null) {
            params.put("cursorCreatedAt", decodedCursor.createdAt());
            params.put("cursorId", decodedCursor.id());
        }
        List<MemberRechargeRequest> rows = memberRechargeRequestMapper.findAdminCursorPage(params);
        boolean hasMore = rows.size() > safeSize;
        List<MemberRechargeRequest> pageItems = hasMore ? rows.subList(0, safeSize) : rows;
        String nextCursor = hasMore
                ? CursorCodecUtils.encode(pageItems.get(pageItems.size() - 1).getCreatedAt(), pageItems.get(pageItems.size() - 1).getId())
                : null;
        return new CursorPageResponse<>(pageItems.stream().map(this::toItemView).toList(), nextCursor, hasMore);
    }

    @Override
    public AdminRechargeDetailView detail(Long id) {
        MemberRechargeRequest request = memberRechargeRequestMapper.findById(id);
        if (request == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "充值申请不存在");
        }
        return toDetailView(request);
    }

    @Override
    public Resource loadScreenshot(Long id) {
        MemberRechargeRequest request = memberRechargeRequestMapper.findById(id);
        if (request == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "充值申请不存在");
        }
        return fileStorageService.loadAsResource(request.getScreenshotPath());
    }

    @Override
    @Transactional
    public AdminRechargeDetailView approve(Long id, AuthenticatedUser currentUser) {
        adminPermissionService.requireSuperAdmin(currentUser);
        MemberRechargeRequest request = memberRechargeRequestMapper.lockById(id);
        if (request == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "充值申请不存在");
        }
        require(RechargeStatus.PENDING.equals(request.getStatus()), ErrorCode.BAD_REQUEST, "充值申请已处理，不能重复通过");
        MemberUser memberUser = memberBalanceService.lockActiveMember(request.getUserId());
        memberBalanceService.creditForRecharge(memberUser, request.getAmount(), request.getRequestNo(), "充值审核通过");
        memberRechargeRequestMapper.approve(id, currentUser.userId());
        return detail(id);
    }

    @Override
    @Transactional
    public AdminRechargeDetailView reject(Long id, String reason, AuthenticatedUser currentUser) {
        adminPermissionService.requireSuperAdmin(currentUser);
        MemberRechargeRequest request = memberRechargeRequestMapper.lockById(id);
        if (request == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "充值申请不存在");
        }
        require(RechargeStatus.PENDING.equals(request.getStatus()), ErrorCode.BAD_REQUEST, "充值申请已处理，不能重复拒绝");
        memberRechargeRequestMapper.reject(id, currentUser.userId(), trim(reason));
        return detail(id);
    }

    private AdminRechargeItemView toItemView(MemberRechargeRequest request) {
        return new AdminRechargeItemView(
                request.getId(),
                request.getRequestNo(),
                request.getUserId(),
                request.getUsername(),
                request.getEmail(),
                request.getAmount(),
                request.getStatus(),
                request.getPayerRemark(),
                request.getCreatedAt(),
                request.getReviewedAt());
    }

    private AdminRechargeDetailView toDetailView(MemberRechargeRequest request) {
        return new AdminRechargeDetailView(
                request.getId(),
                request.getRequestNo(),
                request.getUserId(),
                request.getUsername(),
                request.getEmail(),
                request.getAmount(),
                request.getStatus(),
                request.getPayerRemark(),
                request.getRejectReason(),
                "/api/admin/recharges/" + request.getId() + "/screenshot",
                request.getReviewedByName(),
                request.getCreatedAt(),
                request.getReviewedAt());
    }
}