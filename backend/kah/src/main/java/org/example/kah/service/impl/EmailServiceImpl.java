package org.example.kah.service.impl;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.RandomStringUtils;
import org.example.kah.common.ApiResponse;
import org.example.kah.common.BusinessException;
import org.example.kah.common.ErrorCode;
import org.example.kah.dto.publicapi.CodeSend;
import org.example.kah.dto.publicapi.MemberAuthResponse;
import org.example.kah.dto.publicapi.MemberLoginMailRqs;
import org.example.kah.dto.publicapi.MemberProfileView;
import org.example.kah.dto.publicapi.MemberRegisMailRsp;
import org.example.kah.entity.MemberStatus;
import org.example.kah.entity.MemberUser;
import org.example.kah.mapper.MemberUserMapper;
import org.example.kah.security.JwtService;
import org.example.kah.service.EmailService;
import org.example.kah.service.impl.base.AbstractCrudService;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * {@link EmailService} 默认实现。
 * 负责邮箱验证码发送、邮箱登录和邮箱注册。
 */
@Service
@RequiredArgsConstructor
public class EmailServiceImpl extends AbstractCrudService<MemberUser, Long> implements EmailService {

    private final MemberUserMapper memberUserMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;
    private final JavaMailSender mailSender;

    /**
     * 按主键查询会员实体。
     */
    @Override
    protected MemberUser findEntityById(Long id) {
        return memberUserMapper.findById(id);
    }

    /**
     * 统一实体名称，供抽象基类复用。
     */
    @Override
    protected String entityLabel() {
        return "会员";
    }

    /**
     * 使用邮箱验证码登录。
     */
    @Override
    public MemberAuthResponse login(MemberLoginMailRqs request) {
        verifyCode(request.email(), request.code());
        MemberUser memberUser = memberUserMapper.findByEmail(request.email());
        if (memberUser == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请先注册账号");
        }
        require(MemberStatus.ACTIVE.equals(memberUser.getStatus()), ErrorCode.FORBIDDEN, "账号已被禁用");
        LocalDateTime now = LocalDateTime.now();
        memberUserMapper.updateLoginState(memberUser.getId(), now, now);
        memberUser.setLastLoginAt(now);
        memberUser.setLastSeenAt(now);
        return toAuthResponse(memberUser);
    }

    /**
     * 发送邮箱验证码。
     */
    @Override
    public ApiResponse<Void> sendEmail(CodeSend request) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom("3450624006@qq.com");
        message.setTo(request.email());
        message.setSubject("您的验证码");
        String code = RandomStringUtils.secure().next(6, false, true);
        message.setText(code + "，10分钟内有效");
        stringRedisTemplate.opsForValue().set(request.email(), code, 600, TimeUnit.SECONDS);
        mailSender.send(message);
        return ApiResponse.success();
    }

    /**
     * 使用邮箱验证码注册，并直接返回登录态。
     */
    @Override
    public MemberAuthResponse register(MemberRegisMailRsp request) {
        verifyCode(request.email(), request.code());
        MemberUser byEmail = memberUserMapper.findByEmail(request.email());
        MemberUser byUsername = memberUserMapper.findByUsername(request.username());
        if (byEmail != null || byUsername != null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "用户已经存在");
        }

        LocalDateTime now = LocalDateTime.now();
        MemberUser memberUser = new MemberUser();
        memberUser.setUsername(trim(request.username()));
        memberUser.setMail(trim(request.email()));
        memberUser.setPasswordHash(passwordEncoder.encode(request.password()));
        memberUser.setStatus(MemberStatus.ACTIVE);
        memberUser.setLastLoginAt(now);
        memberUser.setLastSeenAt(now);
        memberUserMapper.insert(memberUser);
        return toAuthResponse(memberUser);
    }

    /**
     * 校验邮箱验证码是否存在且匹配。
     */
    private void verifyCode(String email, String code) {
        String cached = stringRedisTemplate.opsForValue().get(email);
        if (cached == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "验证码已过期");
        }
        if (!cached.equals(code)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "验证码错误");
        }
    }

    /**
     * 组装会员认证响应。
     */
    private MemberAuthResponse toAuthResponse(MemberUser memberUser) {
        return new MemberAuthResponse(
                jwtService.createMemberToken(memberUser),
                "Bearer",
                new MemberProfileView(memberUser.getId(), memberUser.getUsername(), memberUser.getMail()));
    }
}