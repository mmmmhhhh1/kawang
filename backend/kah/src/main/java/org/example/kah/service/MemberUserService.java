package org.example.kah.service;

import org.example.kah.dto.publicapi.MemberAuthResponse;
import org.example.kah.dto.publicapi.MemberLoginRequest;
import org.example.kah.dto.publicapi.MemberProfileView;
import org.example.kah.dto.publicapi.MemberRegisterRequest;
import org.example.kah.security.AuthenticatedUser;

/**
 * 前台会员服务接口。
 * 负责注册、登录和当前会员资料读取。
 */
public interface MemberUserService {

    /**
     * 注册新会员并直接返回登录态。
     *
     * @param request 注册请求
     * @return 会员认证结果
     */
    MemberAuthResponse register(MemberRegisterRequest request);

    /**
     * 会员登录并返回登录态。
     *
     * @param request 登录请求
     * @return 会员认证结果
     */
    MemberAuthResponse login(MemberLoginRequest request);

    /**
     * 获取当前登录会员资料。
     *
     * @param currentUser 当前认证主体
     * @return 会员资料视图
     */
    MemberProfileView me(AuthenticatedUser currentUser);
}
