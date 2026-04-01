package org.example.kah.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "shop.security")
public record ShopSecurityProperties(Jwt jwt, Admin admin) {

    public record Jwt(String secret, long expireHours) {
    }

    public record Admin(String username, String password, String displayName) {
    }
}
