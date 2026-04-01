package org.example.kah.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.example.kah.common.ApiResponse;
import org.example.kah.common.ErrorCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private final Map<String, Deque<Instant>> buckets = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;

    public RateLimitFilter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        Rule rule = resolveRule(request);
        if (rule == null) {
            filterChain.doFilter(request, response);
            return;
        }

        String key = clientIp(request) + ":" + request.getServletPath();
        Deque<Instant> deque = buckets.computeIfAbsent(key, ignored -> new ArrayDeque<>());
        boolean allowed;
        synchronized (deque) {
            Instant threshold = Instant.now().minus(rule.window());
            while (!deque.isEmpty() && deque.peekFirst().isBefore(threshold)) {
                deque.pollFirst();
            }
            allowed = deque.size() < rule.limit();
            if (allowed) {
                deque.offerLast(Instant.now());
            }
        }
        if (!allowed) {
            response.setStatus(429);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write(objectMapper.writeValueAsString(
                    ApiResponse.failure(ErrorCode.TOO_MANY_REQUESTS, "请求过于频繁，请稍后再试")));
            return;
        }
        filterChain.doFilter(request, response);
    }

    private Rule resolveRule(HttpServletRequest request) {
        String path = request.getServletPath();
        String method = request.getMethod();
        if ("POST".equalsIgnoreCase(method) && "/api/orders".equals(path)) {
            return new Rule(10, Duration.ofMinutes(1));
        }
        if ("POST".equalsIgnoreCase(method) && "/api/admin/auth/login".equals(path)) {
            return new Rule(5, Duration.ofMinutes(15));
        }
        if ("POST".equalsIgnoreCase(method) && ("/api/auth/login".equals(path) || "/api/auth/register".equals(path))) {
            return new Rule(10, Duration.ofMinutes(15));
        }
        return null;
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private record Rule(int limit, Duration window) {
    }
}
