package org.example.kah.service.impl;

import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.example.kah.annotation.TrackMemberSeen;
import org.example.kah.common.BusinessException;
import org.example.kah.common.ErrorCode;
import org.example.kah.dto.publicapi.MemberAuthResponse;
import org.example.kah.dto.publicapi.MemberLoginRequest;
import org.example.kah.dto.publicapi.MemberProfileView;
import org.example.kah.dto.publicapi.MemberRegisterRequest;
import org.example.kah.entity.MemberStatus;
import org.example.kah.entity.MemberUser;
import org.example.kah.mapper.MemberUserMapper;
import org.example.kah.security.AuthenticatedUser;
import org.example.kah.security.JwtService;
import org.example.kah.service.MemberActivityCacheService;
import org.example.kah.service.MemberUserService;
import org.example.kah.service.impl.base.AbstractCrudService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * {@link MemberUserService} 默认实现。
 * 负责会员注册、登录以及当前会员资料读取。
 */
@Service
@RequiredArgsConstructor
public class MemberUserServiceImpl extends AbstractCrudService<MemberUser, Long> implements MemberUserService {

    private final MemberUserMapper memberUserMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final MemberActivityCacheService memberActivityCacheService;

    /**
     * 注册会员并直接返回登录态。
     */
    @Override
    public MemberAuthResponse register(MemberRegisterRequest request) {
        String username = trim(request.username());
        if (memberUserMapper.findByUsername(username) != null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "用户名已存在");
        }

        LocalDateTime now = LocalDateTime.now();
        MemberUser memberUser = new MemberUser();
        memberUser.setUsername(username);
        memberUser.setPasswordHash(passwordEncoder.encode(request.password()));
        memberUser.setStatus(MemberStatus.ACTIVE);
        memberUser.setLastLoginAt(now);
        memberUser.setLastSeenAt(now);
        memberUserMapper.insert(memberUser);
        memberActivityCacheService.recordLogin(memberUser.getId(), now);
        return toAuthResponse(memberUser);
    }

    /**
     * 校验会员账号密码并返回登录态。
     */
    @Override
    public MemberAuthResponse login(MemberLoginRequest request) {
        MemberUser memberUser = memberUserMapper.findByUsername(trim(request.username()));
        if (memberUser == null || !passwordEncoder.matches(request.password(), memberUser.getPasswordHash())) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "用户名或密码错误");
        }
        require(MemberStatus.ACTIVE.equals(memberUser.getStatus()), ErrorCode.FORBIDDEN, "账号已被禁用");
        LocalDateTime now = LocalDateTime.now();
        memberUserMapper.updateLoginState(memberUser.getId(), now, now);
        memberUser.setLastLoginAt(now);
        memberUser.setLastSeenAt(now);
        memberActivityCacheService.recordLogin(memberUser.getId(), now);
        return toAuthResponse(memberUser);
    }

    /**
     * 读取当前登录会员资料，并通过切面记录最近活跃时间。
     */
    @Override
    @TrackMemberSeen
    public MemberProfileView me(AuthenticatedUser currentUser) {
        MemberUser memberUser = requireById(currentUser.userId());
        return new MemberProfileView(memberUser.getId(), memberUser.getUsername(), memberUser.getMail());
    }

    @Override
    protected MemberUser findEntityById(Long id) {
        return memberUserMapper.findById(id);
    }

    @Override
    protected String entityLabel() {
        return "会员";
    }

    private MemberAuthResponse toAuthResponse(MemberUser memberUser) {
        return new MemberAuthResponse(
                jwtService.createMemberToken(memberUser),
                "Bearer",
                new MemberProfileView(memberUser.getId(), memberUser.getUsername(), memberUser.getMail()));
    }
}