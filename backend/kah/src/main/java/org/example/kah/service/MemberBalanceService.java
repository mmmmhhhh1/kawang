package org.example.kah.service;

import java.math.BigDecimal;
import org.example.kah.entity.MemberUser;

public interface MemberBalanceService {

    MemberUser lockActiveMember(Long userId);

    MemberUser debitForOrder(MemberUser memberUser, BigDecimal amount, String bizNo, String remark);

    MemberUser creditForRecharge(MemberUser memberUser, BigDecimal amount, String bizNo, String remark);

    MemberUser creditForRefund(MemberUser memberUser, BigDecimal amount, String bizNo, String remark);
}