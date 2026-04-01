package org.example.kah.service;

import java.util.List;
import org.example.kah.dto.admin.AdminNoticeSaveRequest;
import org.example.kah.dto.admin.AdminNoticeView;
import org.example.kah.dto.publicapi.NoticeView;

/**
 * 公告服务接口。
 * 同时提供前台公告展示和后台公告管理能力。
 */
public interface NoticeService {

    /**
     * 查询前台已发布公告。
     *
     * @return 前台公告视图集合
     */
    List<NoticeView> listPublished();

    /**
     * 查询后台公告列表。
     *
     * @return 后台公告视图集合
     */
    List<AdminNoticeView> listAdmin();

    /**
     * 创建公告。
     *
     * @param request 公告保存请求
     * @return 创建后的公告视图
     */
    AdminNoticeView create(AdminNoticeSaveRequest request);

    /**
     * 更新公告。
     *
     * @param id 公告主键
     * @param request 公告保存请求
     * @return 更新后的公告视图
     */
    AdminNoticeView update(Long id, AdminNoticeSaveRequest request);

    /**
     * 更新公告状态。
     *
     * @param id 公告主键
     * @param status 目标状态
     * @return 更新后的公告视图
     */
    AdminNoticeView updateStatus(Long id, String status);
}
