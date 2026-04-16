package org.example.kah.service.impl;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import lombok.RequiredArgsConstructor;
import org.example.kah.common.BusinessException;
import org.example.kah.common.CursorPageResponse;
import org.example.kah.common.ErrorCode;
import org.example.kah.dto.admin.AdminMemberActivityView;
import org.example.kah.dto.admin.AdminMemberDetailView;
import org.example.kah.dto.admin.AdminMemberListView;
import org.example.kah.dto.admin.AdminMemberOrderView;
import org.example.kah.entity.MemberStatus;
import org.example.kah.entity.MemberUser;
import org.example.kah.entity.ShopOrder;
import org.example.kah.entity.ShopOrderAccount;
import org.example.kah.mapper.MemberUserMapper;
import org.example.kah.mapper.ShopOrderAccountMapper;
import org.example.kah.mapper.ShopOrderMapper;
import org.example.kah.service.AdminMemberService;
import org.example.kah.service.MemberActivityCacheService;
import org.example.kah.service.impl.base.AbstractCrudService;
import org.example.kah.support.AsyncTaskSupport;
import org.example.kah.util.CryptoService;
import org.example.kah.util.CursorCodecUtils;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AdminMemberServiceImpl extends AbstractCrudService<MemberUser, Long> implements AdminMemberService {

    private static final String MEMBER_LABEL = "\u4f1a\u5458";
    private static final String MEMBER_NOT_FOUND = "\u4f1a\u5458\u4e0d\u5b58\u5728";
    private static final String INVALID_STATUS = "\u4f1a\u5458\u72b6\u6001\u975e\u6cd5";
    private static final int ORDER_DETAIL_PARALLEL_THRESHOLD = 8;

    private final MemberUserMapper memberUserMapper;
    private final ShopOrderMapper shopOrderMapper;
    private final ShopOrderAccountMapper shopOrderAccountMapper;
    private final CryptoService cryptoService;
    private final MemberActivityCacheService memberActivityCacheService;
    private final AsyncTaskSupport asyncTaskSupport;

    @Override
    public List<AdminMemberListView> list() {
        return memberUserMapper.findAll().stream().map(this::toListView).toList();
    }

    @Override
    public CursorPageResponse<AdminMemberListView> page(int size, String cursor, String keyword, String status) {
        int safeSize = normalizeSize(size, 50);
        CursorCodecUtils.DecodedCursor decodedCursor = CursorCodecUtils.decode(cursor);
        Map<String, Object> params = new HashMap<>();
        params.put("status", trim(status));
        params.put("keyword", trim(keyword));
        params.put("limit", safeSize + 1);
        if (decodedCursor != null) {
            params.put("cursorCreatedAt", decodedCursor.createdAt());
            params.put("cursorId", decodedCursor.id());
        }
        List<MemberUser> rows = memberUserMapper.findAdminCursorPage(params);
        boolean hasMore = rows.size() > safeSize;
        List<MemberUser> pageItems = hasMore ? rows.subList(0, safeSize) : rows;
        String nextCursor = hasMore
                ? CursorCodecUtils.encode(pageItems.get(pageItems.size() - 1).getCreatedAt(), pageItems.get(pageItems.size() - 1).getId())
                : null;
        return new CursorPageResponse<>(pageItems.stream().map(this::toListView).toList(), nextCursor, hasMore);
    }

    @Override
    public AdminMemberDetailView detail(Long id) {
        CompletableFuture<MemberUser> memberFuture = asyncTaskSupport.supplyAsync(() -> memberUserMapper.findById(id));
        CompletableFuture<List<ShopOrder>> orderFuture = asyncTaskSupport.supplyAsync(() -> shopOrderMapper.findByUserId(id));

        MemberUser memberUser = asyncTaskSupport.join(memberFuture);
        if (memberUser == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, MEMBER_NOT_FOUND);
        }

        List<ShopOrder> orders = asyncTaskSupport.join(orderFuture);
        List<AdminMemberOrderView> orderViews = toOrderViews(orders);
        return new AdminMemberDetailView(
                memberUser.getId(),
                memberUser.getUsername(),
                memberUser.getMail(),
                memberUser.getStatus(),
                memberUser.getCreatedAt(),
                memberUser.getUpdatedAt(),
                orderViews);
    }

    @Override
    public List<AdminMemberActivityView> listActivities(List<Long> ids) {
        return memberActivityCacheService.getActivities(ids);
    }

    @Override
    public AdminMemberActivityView activity(Long id) {
        requireById(id);
        return memberActivityCacheService.getActivity(id);
    }

    @Override
    public AdminMemberListView updateStatus(Long id, String status) {
        requireById(id);
        ensureStatus(status);
        memberUserMapper.updateStatus(id, status);
        return toListView(memberUserMapper.findById(id));
    }

    @Override
    protected MemberUser findEntityById(Long id) {
        return memberUserMapper.findById(id);
    }

    @Override
    protected String entityLabel() {
        return MEMBER_LABEL;
    }

    private AdminMemberListView toListView(MemberUser memberUser) {
        return new AdminMemberListView(
                memberUser.getId(),
                memberUser.getUsername(),
                memberUser.getMail(),
                memberUser.getStatus(),
                memberUser.getCreatedAt());
    }

    private List<AdminMemberOrderView> toOrderViews(List<ShopOrder> orders) {
        if (orders.isEmpty()) {
            return List.of();
        }
        if (orders.size() == 1 || orders.size() > ORDER_DETAIL_PARALLEL_THRESHOLD) {
            return orders.stream().map(this::toOrderView).toList();
        }
        List<CompletableFuture<AdminMemberOrderView>> futures = orders.stream()
                .map(order -> asyncTaskSupport.supplyAsync(() -> toOrderView(order)))
                .toList();
        return futures.stream().map(asyncTaskSupport::join).toList();
    }

    private AdminMemberOrderView toOrderView(ShopOrder order) {
        List<String> cardKeys = shopOrderAccountMapper.findByOrderId(order.getId()).stream()
                .map(this::resolveCardKey)
                .filter(Objects::nonNull)
                .toList();
        return new AdminMemberOrderView(
                order.getId(),
                order.getOrderNo(),
                order.getProductId(),
                order.getProductTitleSnapshot(),
                order.getQuantity(),
                order.getTotalAmount(),
                order.getBuyerContact(),
                order.getStatus(),
                order.getCreatedAt(),
                cardKeys);
    }

    private String resolveCardKey(ShopOrderAccount account) {
        if (account.getCardKeyCiphertextSnapshot() != null && !account.getCardKeyCiphertextSnapshot().isBlank()) {
            return cryptoService.decrypt(account.getCardKeyCiphertextSnapshot());
        }
        if (account.getMaskedAccountSnapshot() != null && !account.getMaskedAccountSnapshot().isBlank()) {
            return account.getMaskedAccountSnapshot();
        }
        return null;
    }

    private void ensureStatus(String status) {
        if (!MemberStatus.ACTIVE.equals(status) && !MemberStatus.DISABLED.equals(status)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, INVALID_STATUS);
        }
    }
}