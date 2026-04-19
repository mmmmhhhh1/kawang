package org.example.kah.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;

@ExtendWith(MockitoExtension.class)
class RedisRateLimitServiceTest {

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Test
    void tryAcquireReturnsTrueWhenScriptAllowsRequest() {
        RedisRateLimitService service = new RedisRateLimitService(stringRedisTemplate);
        doReturn(1L)
                .when(stringRedisTemplate)
                .execute(any(), eq(List.of("rate:limit:test")), eq("5"), eq("60000"));

        boolean allowed = service.tryAcquire("rate:limit:test", 5, Duration.ofMinutes(1));

        assertTrue(allowed);
        verify(stringRedisTemplate).execute(any(), eq(List.of("rate:limit:test")), eq("5"), eq("60000"));
    }

    @Test
    void tryAcquireReturnsFalseWhenScriptRejectsRequest() {
        RedisRateLimitService service = new RedisRateLimitService(stringRedisTemplate);
        doReturn(0L)
                .when(stringRedisTemplate)
                .execute(any(), eq(List.of("rate:limit:test")), eq("3"), eq("300000"));

        boolean allowed = service.tryAcquire("rate:limit:test", 3, Duration.ofMinutes(5));

        assertFalse(allowed);
    }

    @Test
    void tryAcquireFallsBackToAllowWhenRedisFails() {
        RedisRateLimitService service = new RedisRateLimitService(stringRedisTemplate);
        doThrow(new IllegalStateException("boom"))
                .when(stringRedisTemplate)
                .execute(any(), eq(List.of("rate:limit:test")), eq("3"), eq("300000"));

        boolean allowed = service.tryAcquire("rate:limit:test", 3, Duration.ofMinutes(5));

        assertTrue(allowed);
    }
}