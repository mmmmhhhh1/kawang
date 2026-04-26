package org.example.kah.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import javax.crypto.SecretKey;
import org.example.kah.config.ShopSecurityProperties;
import org.example.kah.entity.AdminUser;
import org.example.kah.entity.MemberUser;
import org.springframework.stereotype.Component;

@Component
public class JwtService {

    private static final int MIN_SECRET_BYTES = 32;

    private final ShopSecurityProperties securityProperties;

    public JwtService(ShopSecurityProperties securityProperties) {
        this.securityProperties = securityProperties;
        validateSecret(securityProperties.jwt().secret());
    }

    private SecretKey getSigningKey() {
        byte[] keyBytes = securityProperties.jwt().secret().getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    private void validateSecret(String secret) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("shop.security.jwt.secret must not be blank");
        }
        int secretBytes = secret.getBytes(StandardCharsets.UTF_8).length;
        if (secretBytes < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "shop.security.jwt.secret must be at least 32 bytes; current length is " + secretBytes);
        }
    }

    public String createAdminToken(AdminUser adminUser) {
        return createToken(adminUser.getId(), adminUser.getUsername(), UserScope.ADMIN);
    }

    public String createMemberToken(MemberUser memberUser) {
        return createToken(memberUser.getId(), memberUser.getUsername(), UserScope.MEMBER);
    }

    private String createToken(Long userId, String username, String scope) {
        Instant now = Instant.now();
        Instant exp = now.plus(securityProperties.jwt().expireHours(), ChronoUnit.HOURS);

        return Jwts.builder()
                .subject(username)
                .claim("uid", userId)
                .claim("scope", scope)
                .issuedAt(Date.from(now))
                .expiration(Date.from(exp))
                .signWith(getSigningKey())
                .compact();
    }

    public AuthenticatedUser parseAuthenticatedUser(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(getSigningKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            Long uid = claims.get("uid", Long.class);
            String username = claims.getSubject();
            String scope = claims.get("scope", String.class);

            if (uid == null || username == null || scope == null) {
                return null;
            }

            return new AuthenticatedUser(uid, username, scope);
        } catch (JwtException | IllegalArgumentException e) {
            return null;
        }
    }
}
