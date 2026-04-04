package org.example.kah.service.impl;

import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.RandomStringUtils;
import org.example.kah.common.ApiResponse;
import org.example.kah.common.BusinessException;
import org.example.kah.common.ErrorCode;
import org.example.kah.dto.publicapi.*;
import org.example.kah.entity.MemberStatus;
import org.example.kah.entity.MemberUser;
import org.example.kah.mapper.MemberUserMapper;
import org.example.kah.security.JwtService;
import org.example.kah.service.EmailService;
import org.example.kah.service.impl.base.AbstractCrudService;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class EmailServiceImpl extends AbstractCrudService<MemberUser,Long> implements EmailService {
    private final MemberUserMapper memberUserMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;
    private  final JavaMailSender mailSender;

    @Override
    protected MemberUser findEntityById(Long id) {
        return memberUserMapper.findById(id);
    }

    @Override
    protected String entityLabel() {
        return "会员";
    }


    @Override
    public MemberAuthResponse login(MemberLoginMailRqs rqs) {
        String code=stringRedisTemplate.opsForValue().get(rqs.email());
        if (code==null){
            throw new BusinessException(252,"验证码过期");
        }else {
            if(!code.equals(rqs.code())){
                throw new BusinessException(252,"验证码错误");
            }

        }
        MemberUser byEmail = memberUserMapper.findByEmail(rqs.email());
        if (byEmail==null){
            throw new BusinessException(400,"请先注册账号");
        }
        require(MemberStatus.ACTIVE.equals(byEmail.getStatus()), ErrorCode.FORBIDDEN,"账号已被禁用");
        memberUserMapper.updateLastLoginAt(byEmail.getId(), LocalDateTime.now());

        return toAuthResponse(byEmail);

    }

    @Override
    public ApiResponse sendEmail(CodeSend cs) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom("3450624006@qq.com");
        message.setTo(cs.email());
        message.setSubject("您的验证码为");
        String code= RandomStringUtils.secure().next(6,false,true);
        message.setText(code+",10分钟内有效");
        stringRedisTemplate.opsForValue().set(cs.email(), code,600, TimeUnit.SECONDS);
        mailSender.send(message);
        return ApiResponse.success();
    }

    @Override
    public MemberAuthResponse register(MemberRegisMailRsp rqs) {
        MemberUser byEmail = memberUserMapper.findByEmail(rqs.email());
        MemberUser byUsername = memberUserMapper.findByUsername(rqs.username());
        if (byEmail!=null||byUsername!=null){
            throw new BusinessException(400,"用户已经存在");
        }
        MemberUser memberUser = new MemberUser();
        memberUser.setUsername(rqs.username());
        memberUser.setMail(rqs.email());
        memberUser.setPasswordHash(passwordEncoder.encode(rqs.password()));
        memberUser.setStatus(MemberStatus.ACTIVE);
        memberUserMapper.insert(memberUser);
        return toAuthResponse(memberUser);
    }

    private MemberAuthResponse toAuthResponse(MemberUser memberUser) {
        return new MemberAuthResponse(
                jwtService.createMemberToken(memberUser),
                "Bearer",
                new MemberProfileView(memberUser.getId(), memberUser.getUsername(),memberUser.getMail()));
    }

}
