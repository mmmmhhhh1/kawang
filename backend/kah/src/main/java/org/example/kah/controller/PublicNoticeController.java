package org.example.kah.controller;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.example.kah.common.ApiResponse;
import org.example.kah.dto.publicapi.NoticeView;
import org.example.kah.service.NoticeService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 前台公告接口。
 */
@RestController
@RequestMapping("/api/notices")
@RequiredArgsConstructor
public class PublicNoticeController {

    private final NoticeService noticeService;

    /**
     * 查询前台已发布公告。
     *
     * @return 公告列表
     */
    @GetMapping
    public ApiResponse<List<NoticeView>> listPublished() {
        return ApiResponse.success(noticeService.listPublished());
    }
}
