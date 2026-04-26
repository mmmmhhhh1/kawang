package org.example.kah.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.kah.common.ApiResponse;
import org.example.kah.common.ErrorCode;
import org.example.kah.metrics.ShopMetricsService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@RequiredArgsConstructor
@Slf4j
public class RateLimitFilter extends OncePerRequestFilter {

    private static final String TOO_MANY_REQUESTS_MESSAGE = "请求过于频繁，请稍后再试";

    private final ObjectMapper objectMapper;
    private final RedisRateLimitService redisRateLimitService;
    private final ShopMetricsService shopMetricsService;

    @Value("${shop.security.rate-limit.enabled:true}")
    private boolean rateLimitEnabled = true;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (!rateLimitEnabled) {
            filterChain.doFilter(request, response);
            return;
        }

        Rule rule = resolveRule(request);
        if (rule == null) {
            filterChain.doFilter(request, response);
            return;
        }

        String subjectKey = resolveSubjectKey(rule.scope(), request);
        String key = "rate:limit:" + rule.endpoint() + ":" + subjectKey;
        boolean allowed = redisRateLimitService.tryAcquire(key, rule.limit(), rule.window());
        if (allowed) {
            shopMetricsService.recordRateLimitAllowed(rule.endpoint());
            filterChain.doFilter(request, response);
            return;
        }

        shopMetricsService.recordRateLimitBlocked(rule.endpoint());
        log.warn(
                "[rate-limit] blocked endpoint={} path={} subjectKey={} remoteAddr={} userAgent={}",
                rule.endpoint(),
                request.getServletPath(),
                subjectKey,
                request.getRemoteAddr(),
                request.getHeader("User-Agent"));
        response.setStatus(429);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(
                ApiResponse.failure(ErrorCode.TOO_MANY_REQUESTS, TOO_MANY_REQUESTS_MESSAGE)));
    }

    private Rule resolveRule(HttpServletRequest request) {
        String method = request.getMethod();
        String path = request.getServletPath();
        if (!"POST".equalsIgnoreCase(method)) {
            return null;
        }
        if ("/api/orders".equals(path)) {
            return new Rule("orders-create", 10, Duration.ofMinutes(1), SubjectScope.MEMBER);
        }
        if ("/api/auth/recharges".equals(path)) {
            return new Rule("member-recharge-create", 5, Duration.ofMinutes(10), SubjectScope.MEMBER);
        }
        if ("/api/auth/login".equals(path)) {
            return new Rule("member-login", 10, Duration.ofMinutes(15), SubjectScope.IP);
        }
        if ("/api/auth/register".equals(path)) {
            return new Rule("member-register", 10, Duration.ofMinutes(15), SubjectScope.IP);
        }
        if (path != null && path.startsWith("/api/auth/mail/")) {
            return new Rule("member-mail", 5, Duration.ofMinutes(10), SubjectScope.IP);
        }
        if ("/api/admin/auth/login".equals(path)) {
            return new Rule("admin-login", 500000, Duration.ofMinutes(15), SubjectScope.IP);
        }
        return null;
    }

    private String resolveSubjectKey(SubjectScope scope, HttpServletRequest request) {
        AuthenticatedUser currentUser = resolveAuthenticatedUser();
        if (SubjectScope.MEMBER.equals(scope)
                && currentUser != null
                && UserScope.MEMBER.equals(currentUser.scope())
                && currentUser.userId() != null) {
            return "member:" + currentUser.userId();
        }
        if (SubjectScope.ADMIN.equals(scope)
                && currentUser != null
                && UserScope.ADMIN.equals(currentUser.scope())
                && currentUser.userId() != null) {
            return "admin:" + currentUser.userId();
        }
        return "ip:" + clientIp(request);
    }

    private AuthenticatedUser resolveAuthenticatedUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return null;
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof AuthenticatedUser authenticatedUser) {
            return authenticatedUser;
        }
        return null;
    }

    private String clientIp(HttpServletRequest request) {
        String remoteAddr = trimToNull(request.getRemoteAddr());
        if (shouldTrustForwardedHeaders(remoteAddr)) {
            String forwardedIp = firstForwardedIp(request.getHeader("X-Forwarded-For"));
            if (forwardedIp != null) {
                return forwardedIp;
            }
            String realIp = trimToNull(request.getHeader("X-Real-IP"));
            if (realIp != null) {
                return realIp;
            }
        }
        return remoteAddr != null ? remoteAddr : "unknown";
    }

    private boolean shouldTrustForwardedHeaders(String remoteAddr) {
        return remoteAddr != null && (isLoopbackAddress(remoteAddr) || isPrivateNetworkAddress(remoteAddr));
    }

    private boolean isLoopbackAddress(String ip) {
        return "127.0.0.1".equals(ip)
                || "::1".equals(ip)
                || "0:0:0:0:0:0:0:1".equals(ip)
                || "localhost".equalsIgnoreCase(ip);
    }

    private boolean isPrivateNetworkAddress(String ip) {
        String normalized = ip == null ? null : ip.toLowerCase();
        if (normalized == null) {
            return false;
        }
        if (normalized.startsWith("10.") || normalized.startsWith("192.168.") || normalized.startsWith("169.254.")) {
            return true;
        }
        if (normalized.startsWith("172.")) {
            String[] segments = normalized.split("\\.");
            if (segments.length > 1) {
                try {
                    int second = Integer.parseInt(segments[1]);
                    return second >= 16 && second <= 31;
                } catch (NumberFormatException ignored) {
                    return false;
                }
            }
        }
        return normalized.startsWith("fc") || normalized.startsWith("fd") || normalized.startsWith("fe80:");
    }

    private String firstForwardedIp(String forwardedHeader) {
        if (forwardedHeader == null || forwardedHeader.isBlank()) {
            return null;
        }
        for (String part : forwardedHeader.split(",")) {
            String candidate = trimToNull(part);
            if (candidate != null && !"unknown".equalsIgnoreCase(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private enum SubjectScope {
        IP,
        MEMBER,
        ADMIN
    }

    private record Rule(String endpoint, int limit, Duration window, SubjectScope scope) {
    }
}
