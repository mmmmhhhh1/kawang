package org.example.kah.aspect;

import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.example.kah.annotation.TrackMemberSeen;
import org.example.kah.security.AuthenticatedUser;
import org.example.kah.service.MemberActivityCacheService;
import org.springframework.stereotype.Component;

/**
 * 统一拦截被 {@link TrackMemberSeen} 标记的方法，
 * 在方法成功返回后记录会员最近活跃时间。
 */
@Aspect
@Component
@RequiredArgsConstructor
public class MemberActivityAspect {

    private final MemberActivityCacheService memberActivityCacheService;

    @Around("@annotation(trackMemberSeen)")
    public Object trackSeen(ProceedingJoinPoint joinPoint, TrackMemberSeen trackMemberSeen) throws Throwable {
        Object result = joinPoint.proceed();
        Long userId = resolveUserId(joinPoint.getArgs());
        if (userId != null) {
            memberActivityCacheService.recordSeen(userId);
        }
        return result;
    }

    private Long resolveUserId(Object[] args) {
        for (Object arg : args) {
            if (arg instanceof AuthenticatedUser currentUser && currentUser != null) {
                return currentUser.userId();
            }
        }
        for (Object arg : args) {
            if (arg instanceof Long userId) {
                return userId;
            }
        }
        return null;
    }
}