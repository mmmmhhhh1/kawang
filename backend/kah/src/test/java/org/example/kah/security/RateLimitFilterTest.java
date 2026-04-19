package org.example.kah.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import java.time.Duration;
import org.example.kah.metrics.ShopMetricsService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
class RateLimitFilterTest {

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private RedisRateLimitService redisRateLimitService;

    @Mock
    private ShopMetricsService shopMetricsService;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void memberOrderLimitUsesMemberScopedKey() throws Exception {
        RateLimitFilter filter = new RateLimitFilter(objectMapper, redisRateLimitService, shopMetricsService);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new AuthenticatedUser(123L, "member", UserScope.MEMBER), null));
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/orders");
        request.setServletPath("/api/orders");
        request.setRemoteAddr("127.0.0.1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = (req, res) -> ((MockHttpServletResponse) res).setStatus(204);
        when(redisRateLimitService.tryAcquire(
                        eq("rate:limit:orders-create:member:123"),
                        eq(10),
                        eq(Duration.ofMinutes(1))))
                .thenReturn(true);

        filter.doFilterInternal(request, response, chain);

        assertEquals(204, response.getStatus());
        verify(shopMetricsService).recordRateLimitAllowed("orders-create");
    }

    @Test
    void blockedLoginRequestReturns429() throws Exception {
        RateLimitFilter filter = new RateLimitFilter(objectMapper, redisRateLimitService, shopMetricsService);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login");
        request.setServletPath("/api/auth/login");
        request.setRemoteAddr("127.0.0.1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = (req, res) -> ((MockHttpServletResponse) res).setStatus(204);
        when(redisRateLimitService.tryAcquire(
                        eq("rate:limit:member-login:ip:127.0.0.1"),
                        eq(10),
                        eq(Duration.ofMinutes(15))))
                .thenReturn(false);
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        filter.doFilterInternal(request, response, chain);

        assertEquals(429, response.getStatus());
        assertEquals("{}", response.getContentAsString());
        verify(shopMetricsService).recordRateLimitBlocked("member-login");
        verify(shopMetricsService, never()).recordRateLimitAllowed("member-login");
    }
}