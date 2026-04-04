package org.example.kah.controller;

import ch.qos.logback.core.testUtil.RandomUtil;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.RandomStringUtils;
import org.example.kah.common.ApiResponse;
import org.example.kah.common.BusinessException;
import org.example.kah.dto.publicapi.*;
import org.example.kah.security.AuthenticatedUser;
import org.example.kah.service.EmailService;
import org.example.kah.service.MemberUserService;
import org.example.kah.service.OrderFacadeService;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/**
 * 前台会员注册、登录和“我的订单”接口。
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class PublicAuthController {

    private final MemberUserService memberUserService;
    private final OrderFacadeService orderFacadeService;
    private final StringRedisTemplate stringRedisTemplate;
    private final EmailService emailService;
    /**
     * 前台会员注册接口。
     *
     * @param request 注册请求
     * @return 注册后的登录态
     */
    @PostMapping("/register")
    public ApiResponse<MemberAuthResponse> register(@Valid @RequestBody MemberRegisterRequest request) {
        return ApiResponse.success(memberUserService.register(request));
    }

    /**
     * 前台会员登录接口。
     *
     * @param request 登录请求
     * @return 登录后的认证结果
     */
    @PostMapping("/login")
    public ApiResponse<MemberAuthResponse> login(@Valid @RequestBody MemberLoginRequest request) {
        return ApiResponse.success(memberUserService.login(request));
    }

    /**
     * 当前登录会员资料接口。
     *
     * @param authentication Spring Security 当前认证对象
     * @return 当前会员资料
     */
    @GetMapping("/me")
    public ApiResponse<MemberProfileView> me(Authentication authentication) {
        return ApiResponse.success(memberUserService.me((AuthenticatedUser) authentication.getPrincipal()));
    }

    /**
     * 当前登录会员订单列表接口。
     *
     * @param authentication Spring Security 当前认证对象
     * @return 当前会员已绑定订单列表
     */
    @GetMapping("/orders")
    public ApiResponse<List<OrderQueryView>> myOrders(Authentication authentication) {
        AuthenticatedUser currentUser = (AuthenticatedUser) authentication.getPrincipal();
        return ApiResponse.success(orderFacadeService.listByUser(currentUser.userId()));
    }
    /**
     * 发送验证码
     * @param cs 封装前端传过来的邮件和使用场景
     * @retuen 成功
     * */
    @PostMapping("/mail/send-code")
    public ApiResponse sendMail(@RequestBody CodeSend cs) {
      return emailService.sendEmail(cs);
    }

    /**
     * 发送验证码
     * @param rqs 封装前端传过来的邮件和验证码
     * @retuen 成功
     * */
    @PostMapping("/mail/login")
    public ApiResponse<MemberAuthResponse> emailLogin(@RequestBody MemberLoginMailRqs rqs) {

        return ApiResponse.success(emailService.login(rqs));
    }

    @PostMapping("/mail/register")
    public ApiResponse<MemberAuthResponse> emailRegister(@RequestBody MemberRegisMailRsp rqs) {

        return ApiResponse.success(emailService.register(rqs));
    }
}
