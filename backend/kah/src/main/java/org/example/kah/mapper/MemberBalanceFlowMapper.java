package org.example.kah.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.example.kah.entity.MemberBalanceFlow;

@Mapper
public interface MemberBalanceFlowMapper {

    @Insert("""
            INSERT INTO member_balance_flow (
                user_id, biz_type, biz_no, direction, amount, balance_before, balance_after, remark
            ) VALUES (
                #{userId}, #{bizType}, #{bizNo}, #{direction}, #{amount}, #{balanceBefore}, #{balanceAfter}, #{remark}
            )
            """)
    int insert(MemberBalanceFlow flow);
}