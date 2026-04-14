package org.example.kah.controller;

import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.example.kah.common.ApiResponse;
import org.example.kah.common.CursorPageResponse;
import org.example.kah.dto.publicapi.CodeSend;
import org.example.kah.dto.publicapi.MemberAuthResponse;
import org.example.kah.dto.publicapi.MemberLoginMailRqs;
import org.example.kah.dto.publicapi.MemberLoginRequest;
import org.example.kah.dto.publicapi.MemberProfileView;
import org.example.kah.dto.publicapi.MemberRechargeItemView;
import org.example.kah.dto.publicapi.MemberRegisMailRsp;
import org.example.kah.dto.publicapi.MemberRegisterRequest;
import org.example.kah.dto.publicapi.OrderQueryView;
import org.example.kah.security.AuthenticatedUser;
import org.example.kah.service.EmailService;
import org.example.kah.service.MemberRechargeService;
import org.example.kah.service.MemberUserService;
import org.example.kah.service.OrderFacadeService;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 前台会员鉴权与会员中心接口。
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class PublicAuthController {

    private final MemberUserService memberUserService;
    private final OrderFacadeService orderFacadeService;
    private final EmailService emailService;
    private final MemberRechargeService memberRechargeService;

    /**
     * 普通会员注册。
     */
    @PostMapping("/register")
    public ApiResponse<MemberAuthResponse> register(@Valid @RequestBody MemberRegisterRequest request) {
        return ApiResponse.success(memberUserService.register(request));
    }

    /**
     * 普通会员登录。
     */
    @PostMapping("/login")
    public ApiResponse<MemberAuthResponse> login(@Valid @RequestBody MemberLoginRequest request) {
        return ApiResponse.success(memberUserService.login(request));
    }

    /**
     * 获取当前登录会员资料。
     */
    @GetMapping("/me")
    public ApiResponse<MemberProfileView> me(Authentication authentication) {
        return ApiResponse.success(memberUserService.me((AuthenticatedUser) authentication.getPrincipal()));
    }

    /**
     * 获取当前登录会员订单。
     */
    @GetMapping("/orders")
    public ApiResponse<List<OrderQueryView>> myOrders(Authentication authentication) {
        AuthenticatedUser currentUser = (AuthenticatedUser) authentication.getPrincipal();
        return ApiResponse.success(orderFacadeService.listByUser(currentUser.userId()));
    }

    /**
     * 发送邮箱验证码。
     */
    @PostMapping("/mail/send-code")
    public ApiResponse<Void> sendMail(@RequestBody CodeSend request) {
        return emailService.sendEmail(request);
    }

    /**
     * 邮箱验证码登录。
     */
    @PostMapping("/mail/login")
    public ApiResponse<MemberAuthResponse> emailLogin(@RequestBody MemberLoginMailRqs request) {
        return ApiResponse.success(emailService.login(request));
    }

    /**
     * 邮箱验证码注册。
     */
    @PostMapping("/mail/register")
    public ApiResponse<MemberAuthResponse> emailRegister(@RequestBody MemberRegisMailRsp request) {
        return ApiResponse.success(emailService.register(request));
    }

    /**
     * 查询当前会员充值记录。
     */
    @GetMapping("/recharges")
    public ApiResponse<CursorPageResponse<MemberRechargeItemView>> myRecharges(
            Authentication authentication,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String cursor) {
        AuthenticatedUser currentUser = (AuthenticatedUser) authentication.getPrincipal();
        return ApiResponse.success(memberRechargeService.listMine(currentUser, size, cursor));
    }

    /**
     * 提交充值申请。
     */
    @PostMapping(value = "/recharges", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<MemberRechargeItemView> createRecharge(
            Authentication authentication,
            @RequestParam BigDecimal amount,
            @RequestParam MultipartFile screenshot,
            @RequestParam(required = false) String payerRemark) {
        AuthenticatedUser currentUser = (AuthenticatedUser) authentication.getPrincipal();
        return ApiResponse.success(memberRechargeService.create(currentUser, amount, screenshot, payerRemark));
    }

    /**
     * 查看当前会员自己的充值截图。
     */
    @GetMapping("/recharges/{id}/screenshot")
    public ResponseEntity<Resource> myRechargeScreenshot(Authentication authentication, @PathVariable Long id) {
        AuthenticatedUser currentUser = (AuthenticatedUser) authentication.getPrincipal();
        Resource resource = memberRechargeService.loadMineScreenshot(currentUser, id);
        MediaType mediaType = MediaTypeFactory.getMediaType(resource).orElse(MediaType.APPLICATION_OCTET_STREAM);
        return ResponseEntity.ok().contentType(mediaType).body(resource);
    }
}