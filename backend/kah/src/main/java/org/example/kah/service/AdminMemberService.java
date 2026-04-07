package org.example.kah.service;

import java.util.List;
import org.example.kah.dto.admin.AdminMemberActivityView;
import org.example.kah.dto.admin.AdminMemberDetailView;
import org.example.kah.dto.admin.AdminMemberListView;

/**
 * 后台会员管理服务接口。
 */
public interface AdminMemberService {

    /** 查询后台会员基础列表。 */
    List<AdminMemberListView> list();

    /** 查询会员详情及其订单。 */
    AdminMemberDetailView detail(Long id);

    /** 批量查询会员活动信息。 */
    List<AdminMemberActivityView> listActivities(List<Long> ids);

    /** 查询单个会员活动信息。 */
    AdminMemberActivityView activity(Long id);

    /** 更新会员状态。 */
    AdminMemberListView updateStatus(Long id, String status);
}