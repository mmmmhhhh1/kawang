package org.example.kah.service.impl;

import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import org.example.kah.common.BusinessException;
import org.example.kah.common.ErrorCode;
import org.example.kah.entity.BalanceBizType;
import org.example.kah.entity.BalanceDirection;
import org.example.kah.entity.MemberBalanceFlow;
import org.example.kah.entity.MemberStatus;
import org.example.kah.entity.MemberUser;
import org.example.kah.mapper.MemberBalanceFlowMapper;
import org.example.kah.mapper.MemberUserMapper;
import org.example.kah.metrics.ShopMetricsService;
import org.example.kah.service.MemberBalanceService;
import org.example.kah.service.impl.base.AbstractServiceSupport;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MemberBalanceServiceImpl extends AbstractServiceSupport implements MemberBalanceService {

    private static final int ORDER_DEBIT_RETRY_TIMES = 3;

    private final MemberUserMapper memberUserMapper;
    private final MemberBalanceFlowMapper memberBalanceFlowMapper;
    private final ShopMetricsService shopMetricsService;

    @Override
    public MemberUser lockActiveMember(Long userId) {
        MemberUser memberUser = memberUserMapper.lockById(userId);
        if (memberUser == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "会员不存在");
        }
        require(MemberStatus.ACTIVE.equals(memberUser.getStatus()), ErrorCode.FORBIDDEN, "会员账号已被禁用");
        if (memberUser.getBalance() == null) {
            memberUser.setBalance(BigDecimal.ZERO);
        }
        return memberUser;
    }

    @Override
    public MemberUser debitForOrder(Long userId, BigDecimal amount, String bizNo, String remark) {
        require(amount != null && amount.compareTo(BigDecimal.ZERO) > 0, "扣款金额必须大于 0");
        for (int attempt = 0; attempt < ORDER_DEBIT_RETRY_TIMES; attempt++) {
            MemberUser memberUser = memberUserMapper.findById(userId);
            if (memberUser == null) {
                throw new BusinessException(ErrorCode.NOT_FOUND, "会员不存在");
            }
            require(MemberStatus.ACTIVE.equals(memberUser.getStatus()), ErrorCode.FORBIDDEN, "会员账号已被禁用");

            BigDecimal before = safeBalance(memberUser.getBalance());
            BigDecimal after = before.subtract(amount);
            require(after.compareTo(BigDecimal.ZERO) >= 0, "余额不足");

            int updated = memberUserMapper.debitBalanceIfEnough(memberUser.getId(), amount, before);
            if (updated == 1) {
                try {
                    saveFlow(memberUser.getId(), BalanceBizType.ORDER_DEBIT, bizNo, BalanceDirection.OUT, amount, before, after, remark);
                } catch (DuplicateKeyException exception) {
                    shopMetricsService.recordBalanceDebitConflict();
                    throw new BusinessException(ErrorCode.BAD_REQUEST, "订单扣款已经处理过了");
                }
                memberUser.setBalance(after);
                shopMetricsService.recordBalanceDebitSuccess();
                return memberUser;
            }
        }
        shopMetricsService.recordBalanceDebitConflict();
        throw new BusinessException(ErrorCode.BAD_REQUEST, "余额变动频繁，请稍后重试");
    }

    @Override
    public MemberUser creditForRecharge(MemberUser memberUser, BigDecimal amount, String bizNo, String remark) {
        require(amount != null && amount.compareTo(BigDecimal.ZERO) > 0, "充值金额必须大于 0");
        BigDecimal before = safeBalance(memberUser.getBalance());
        BigDecimal after = before.add(amount);
        try {
            saveFlow(memberUser.getId(), BalanceBizType.RECHARGE_APPROVED, bizNo, BalanceDirection.IN, amount, before, after, remark);
        } catch (DuplicateKeyException exception) {
            shopMetricsService.recordRechargeDuplicate();
            throw new BusinessException(ErrorCode.BAD_REQUEST, "这笔充值已经入账");
        }
        memberUserMapper.updateBalance(memberUser.getId(), after);
        memberUser.setBalance(after);
        return memberUser;
    }

    @Override
    public MemberUser creditForRefund(MemberUser memberUser, BigDecimal amount, String bizNo, String remark) {
        require(amount != null && amount.compareTo(BigDecimal.ZERO) > 0, "退款金额必须大于 0");
        BigDecimal before = safeBalance(memberUser.getBalance());
        BigDecimal after = before.add(amount);
        try {
            saveFlow(memberUser.getId(), BalanceBizType.ORDER_REFUND, bizNo, BalanceDirection.IN, amount, before, after, remark);
        } catch (DuplicateKeyException exception) {
            shopMetricsService.recordRefundDuplicate();
            throw new BusinessException(ErrorCode.BAD_REQUEST, "订单退款已经处理过了");
        }
        memberUserMapper.updateBalance(memberUser.getId(), after);
        memberUser.setBalance(after);
        return memberUser;
    }

    private void saveFlow(Long userId, String bizType, String bizNo, String direction, BigDecimal amount, BigDecimal before, BigDecimal after, String remark) {
        MemberBalanceFlow flow = new MemberBalanceFlow();
        flow.setUserId(userId);
        flow.setBizType(bizType);
        flow.setBizNo(bizNo);
        flow.setDirection(direction);
        flow.setAmount(amount);
        flow.setBalanceBefore(before);
        flow.setBalanceAfter(after);
        flow.setRemark(remark);
        memberBalanceFlowMapper.insert(flow);
    }

    private BigDecimal safeBalance(BigDecimal balance) {
        return balance == null ? BigDecimal.ZERO : balance;
    }
}