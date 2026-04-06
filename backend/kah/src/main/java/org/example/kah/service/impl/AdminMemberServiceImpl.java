package org.example.kah.service.impl;

import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.example.kah.common.BusinessException;
import org.example.kah.common.ErrorCode;
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
import org.example.kah.service.impl.base.AbstractCrudService;
import org.example.kah.util.CryptoService;
import org.springframework.stereotype.Service;

/**
 * {@link AdminMemberService} 默认实现。
 * 负责后台会员列表、会员详情和会员状态切换。
 */
@Service
@RequiredArgsConstructor
public class AdminMemberServiceImpl extends AbstractCrudService<MemberUser, Long> implements AdminMemberService {

    private final MemberUserMapper memberUserMapper;
    private final ShopOrderMapper shopOrderMapper;
    private final ShopOrderAccountMapper shopOrderAccountMapper;
    private final CryptoService cryptoService;

    /**
     * 查询后台会员列表。
     */
    @Override
    public List<AdminMemberListView> list() {
        return memberUserMapper.findAll().stream().map(this::toListView).toList();
    }

    /**
     * 查询会员详情和其已购订单。
     */
    @Override
    public AdminMemberDetailView detail(Long id) {
        MemberUser memberUser = requireById(id);
        List<AdminMemberOrderView> orders = shopOrderMapper.findByUserId(id).stream()
                .map(this::toOrderView)
                .toList();
        return new AdminMemberDetailView(
                memberUser.getId(),
                memberUser.getUsername(),
                memberUser.getMail(),
                memberUser.getStatus(),
                memberUser.getLastSeenAt(),
                memberUser.getLastLoginAt(),
                memberUser.getCreatedAt(),
                memberUser.getUpdatedAt(),
                orders);
    }

    /**
     * 切换会员账号状态。
     */
    @Override
    public AdminMemberListView updateStatus(Long id, String status) {
        MemberUser memberUser = requireById(id);
        ensureStatus(status);
        memberUserMapper.updateStatus(id, status);
        memberUser.setStatus(status);
        return toListView(memberUserMapper.findById(id));
    }

    /**
     * 按主键查询会员实体。
     */
    @Override
    protected MemberUser findEntityById(Long id) {
        return memberUserMapper.findById(id);
    }

    /**
     * 统一会员实体名称，复用抽象基类的未找到提示。
     */
    @Override
    protected String entityLabel() {
        return "会员";
    }

    /**
     * 将会员实体映射为列表视图。
     */
    private AdminMemberListView toListView(MemberUser memberUser) {
        return new AdminMemberListView(
                memberUser.getId(),
                memberUser.getUsername(),
                memberUser.getMail(),
                memberUser.getStatus(),
                memberUser.getLastSeenAt(),
                memberUser.getLastLoginAt(),
                memberUser.getCreatedAt());
    }

    /**
     * 将会员订单映射为后台详情视图。
     */
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

    /**
     * 从订单卡密快照中恢复卡密正文。
     */
    private String resolveCardKey(ShopOrderAccount account) {
        if (account.getCardKeyCiphertextSnapshot() != null && !account.getCardKeyCiphertextSnapshot().isBlank()) {
            return cryptoService.decrypt(account.getCardKeyCiphertextSnapshot());
        }
        if (account.getMaskedAccountSnapshot() != null && !account.getMaskedAccountSnapshot().isBlank()) {
            return account.getMaskedAccountSnapshot();
        }
        return null;
    }

    /**
     * 校验会员状态是否合法。
     */
    private void ensureStatus(String status) {
        if (!MemberStatus.ACTIVE.equals(status) && !MemberStatus.DISABLED.equals(status)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "会员状态非法");
        }
    }
}