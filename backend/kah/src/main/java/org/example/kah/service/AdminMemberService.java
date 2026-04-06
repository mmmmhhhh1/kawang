package org.example.kah.service;

import java.util.List;
import org.example.kah.dto.admin.AdminMemberDetailView;
import org.example.kah.dto.admin.AdminMemberListView;

/**
 * 后台会员管理服务接口。
 * 负责会员列表、会员详情和会员启停用。
 */
public interface AdminMemberService {

    /** 查询后台会员列表。 */
    List<AdminMemberListView> list();

    /** 查询会员详情及其订单。 */
    AdminMemberDetailView detail(Long id);

    /** 更新会员状态。 */
    AdminMemberListView updateStatus(Long id, String status);
}