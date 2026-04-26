package org.example.kah.service.impl;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.example.kah.common.BusinessException;
import org.example.kah.common.CursorPageResponse;
import org.example.kah.common.ErrorCode;
import org.example.kah.dto.admin.AdminNotificationEvent;
import org.example.kah.dto.publicapi.MemberRechargeItemView;
import org.example.kah.entity.MemberRechargeRequest;
import org.example.kah.entity.MemberUser;
import org.example.kah.mapper.MemberRechargeRequestMapper;
import org.example.kah.mapper.MemberUserMapper;
import org.example.kah.security.AuthenticatedUser;
import org.example.kah.service.AdminNotificationService;
import org.example.kah.service.FileStorageService;
import org.example.kah.service.MemberRechargeService;
import org.example.kah.service.SupportRealtimeService;
import org.example.kah.service.impl.base.AbstractServiceSupport;
import org.example.kah.util.CursorCodecUtils;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class MemberRechargeServiceImpl extends AbstractServiceSupport implements MemberRechargeService {

    private final MemberRechargeRequestMapper memberRechargeRequestMapper;
    private final MemberUserMapper memberUserMapper;
    private final FileStorageService fileStorageService;
    private final AdminNotificationService adminNotificationService;
    private final SupportRealtimeService supportRealtimeService;

    @Override
    public CursorPageResponse<MemberRechargeItemView> listMine(AuthenticatedUser currentUser, int size, String cursor) {
        int safeSize = normalizeSize(size, 30);
        CursorCodecUtils.DecodedCursor decodedCursor = CursorCodecUtils.decode(cursor);
        java.util.Map<String, Object> params = new java.util.HashMap<>();
        params.put("userId", currentUser.userId());
        params.put("limit", safeSize + 1);
        if (decodedCursor != null) {
            params.put("cursorCreatedAt", decodedCursor.createdAt());
            params.put("cursorId", decodedCursor.id());
        }
        List<MemberRechargeRequest> rows = memberRechargeRequestMapper.findMemberCursorPage(params);
        boolean hasMore = rows.size() > safeSize;
        List<MemberRechargeRequest> pageItems = hasMore ? rows.subList(0, safeSize) : rows;
        String nextCursor = hasMore
                ? CursorCodecUtils.encode(pageItems.get(pageItems.size() - 1).getCreatedAt(), pageItems.get(pageItems.size() - 1).getId())
                : null;
        return new CursorPageResponse<>(pageItems.stream().map(this::toMemberView).toList(), nextCursor, hasMore);
    }

    @Override
    public MemberRechargeItemView create(AuthenticatedUser currentUser, BigDecimal amount, MultipartFile screenshot, String payerRemark) {
        require(amount != null && amount.compareTo(new BigDecimal("0.01")) >= 0, "充值金额必须大于 0");
        String relativePath = fileStorageService.saveRechargeScreenshot(screenshot);

        MemberRechargeRequest request = new MemberRechargeRequest();
        request.setRequestNo("RC" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase());
        request.setUserId(currentUser.userId());
        request.setAmount(amount);
        request.setStatus("PENDING");
        request.setScreenshotPath(relativePath);
        request.setPayerRemark(trim(payerRemark));
        memberRechargeRequestMapper.insert(request);

        MemberRechargeRequest created = memberRechargeRequestMapper.findById(request.getId());
        AdminNotificationEvent event = new AdminNotificationEvent(
                "RECHARGE_CREATED",
                "收到新的充值申请",
                buildRechargeNotificationMessage(created),
                created.getId(),
                created.getCreatedAt());
        adminNotificationService.broadcast(event);
        supportRealtimeService.dispatchRechargeCreated(event);
        return toMemberView(created);
    }

    @Override
    public Resource loadMineScreenshot(AuthenticatedUser currentUser, Long rechargeId) {
        MemberRechargeRequest request = memberRechargeRequestMapper.findById(rechargeId);
        if (request == null || !currentUser.userId().equals(request.getUserId())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "充值申请不存在");
        }
        return fileStorageService.loadAsResource(request.getScreenshotPath());
    }

    private String buildRechargeNotificationMessage(MemberRechargeRequest request) {
        MemberUser memberUser = memberUserMapper.findById(request.getUserId());
        String username = memberUser == null ? null : trim(memberUser.getUsername());
        String email = memberUser == null ? null : trim(memberUser.getMail());
        String displayName = username != null ? username : (email != null ? email : "会员");
        String amountText = request.getAmount() == null ? "0" : request.getAmount().stripTrailingZeros().toPlainString();
        StringBuilder message = new StringBuilder("用户“")
                .append(displayName)
                .append("”提交了一笔 ")
                .append(amountText)
                .append(" 元的充值申请");
        if (email != null && !email.equals(displayName)) {
            message.append("（").append(email).append("）");
        }
        message.append("，请尽快审核入账。单号：").append(request.getRequestNo());
        return message.toString();
    }

    private MemberRechargeItemView toMemberView(MemberRechargeRequest request) {
        return new MemberRechargeItemView(
                request.getId(),
                request.getRequestNo(),
                request.getAmount(),
                request.getStatus(),
                request.getPayerRemark(),
                request.getRejectReason(),
                request.getReviewedAt(),
                request.getCreatedAt());
    }
}
