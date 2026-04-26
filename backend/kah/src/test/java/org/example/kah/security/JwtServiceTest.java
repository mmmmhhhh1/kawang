package org.example.kah.security;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.example.kah.config.ShopSecurityProperties;
import org.junit.jupiter.api.Test;

class JwtServiceTest {

    @Test
    void rejectsShortSigningSecret() {
        ShopSecurityProperties properties = new ShopSecurityProperties(
                new ShopSecurityProperties.Jwt("short-secret", 12),
                new ShopSecurityProperties.Admin("admin", "password", "Super Admin"));

        assertThrows(IllegalStateException.class, () -> new JwtService(properties));
    }

    @Test
    void acceptsSecretWithAtLeastThirtyTwoBytes() {
        ShopSecurityProperties properties = new ShopSecurityProperties(
                new ShopSecurityProperties.Jwt("0123456789abcdef0123456789abcdef", 12),
                new ShopSecurityProperties.Admin("admin", "password", "Super Admin"));

        assertDoesNotThrow(() -> new JwtService(properties));
    }
}
