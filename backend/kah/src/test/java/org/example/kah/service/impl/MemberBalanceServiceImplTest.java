package org.example.kah.service.impl;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import org.example.kah.common.BusinessException;
import org.example.kah.entity.MemberStatus;
import org.example.kah.entity.MemberUser;
import org.example.kah.mapper.MemberBalanceFlowMapper;
import org.example.kah.mapper.MemberUserMapper;
import org.example.kah.metrics.ShopMetricsService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

@ExtendWith(MockitoExtension.class)
class MemberBalanceServiceImplTest {

    @Mock
    private MemberUserMapper memberUserMapper;

    @Mock
    private MemberBalanceFlowMapper memberBalanceFlowMapper;

    @Mock
    private ShopMetricsService shopMetricsService;

    @Test
    void creditForRechargeStopsWhenFlowAlreadyExists() {
        MemberBalanceServiceImpl service = new MemberBalanceServiceImpl(memberUserMapper, memberBalanceFlowMapper, shopMetricsService);
        MemberUser memberUser = new MemberUser();
        memberUser.setId(7L);
        memberUser.setBalance(new BigDecimal("10.00"));
        memberUser.setStatus(MemberStatus.ACTIVE);
        when(memberBalanceFlowMapper.insert(any())).thenThrow(new DuplicateKeyException("duplicate"));

        assertThrows(BusinessException.class, () -> service.creditForRecharge(memberUser, new BigDecimal("5.00"), "RC001", "充值"));

        verify(shopMetricsService).recordRechargeDuplicate();
        verify(memberUserMapper, never()).updateBalance(any(), any());
    }

    @Test
    void debitForOrderRecordsConflictAfterRetries() {
        MemberBalanceServiceImpl service = new MemberBalanceServiceImpl(memberUserMapper, memberBalanceFlowMapper, shopMetricsService);
        MemberUser memberUser = new MemberUser();
        memberUser.setId(9L);
        memberUser.setBalance(new BigDecimal("20.00"));
        memberUser.setStatus(MemberStatus.ACTIVE);
        when(memberUserMapper.findById(9L)).thenReturn(memberUser);
        when(memberUserMapper.debitBalanceIfEnough(9L, new BigDecimal("5.00"), new BigDecimal("20.00"))).thenReturn(0);

        assertThrows(BusinessException.class, () -> service.debitForOrder(9L, new BigDecimal("5.00"), "ORD001", "下单"));

        verify(shopMetricsService).recordBalanceDebitConflict();
    }
}