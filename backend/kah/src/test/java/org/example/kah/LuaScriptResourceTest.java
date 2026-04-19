package org.example.kah;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

class LuaScriptResourceTest {

    @Test
    void rateLimitScriptUsesValidRedisCommandQuoting() throws IOException {
        String script = readScript("lua/rate_limit_fixed_window.lua");

        assertFalse(script.contains("''"));
        assertTrue(script.contains("redis.call('GET'"));
        assertTrue(script.contains("redis.call('INCR'"));
        assertTrue(script.contains("redis.call('PEXPIRE'"));
    }

    @Test
    void addAvailableScriptUsesValidRedisCommandQuoting() throws IOException {
        String script = readScript("lua/order_pool_add_available.lua");

        assertFalse(script.contains("''"));
        assertTrue(script.contains("redis.call('ZSCORE'"));
        assertTrue(script.contains("redis.call('ZADD'"));
        assertTrue(script.contains("'NX'"));
    }

    private String readScript(String location) throws IOException {
        return new String(new ClassPathResource(location).getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    }
}