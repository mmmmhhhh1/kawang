package org.example.kah.security;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import lombok.RequiredArgsConstructor;
import org.example.kah.config.ShopSecurityProperties;
import org.example.kah.entity.AdminUser;
import org.example.kah.entity.MemberUser;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class JwtService {

    private final ShopSecurityProperties securityProperties;
    private final ObjectMapper objectMapper;

    /**
     * 为后台管理员生成带 scope 的 JWT。
     */
    public String createAdminToken(AdminUser adminUser) {
        return createToken(adminUser.getId(), adminUser.getUsername(), UserScope.ADMIN);
    }

    /**
     * 为前台会员生成带 scope 的 JWT。
     */
    public String createMemberToken(MemberUser memberUser) {
        return createToken(memberUser.getId(), memberUser.getUsername(), UserScope.MEMBER);
    }

    private String createToken(Long userId, String username, String scope) {
        try {
            Instant now = Instant.now();
            Map<String, Object> header = Map.of("alg", "HS256", "typ", "JWT");
            Map<String, Object> payload = Map.of(
                    "sub", username,
                    "uid", userId,
                    "iat", now.getEpochSecond(),
                    "exp", now.plus(securityProperties.jwt().expireHours(), ChronoUnit.HOURS).getEpochSecond(),
                    "scope", scope);
            String headerPart = encode(header);
            String payloadPart = encode(payload);
            String signature = sign(headerPart + "." + payloadPart);
            return headerPart + "." + payloadPart + "." + signature;
        } catch (Exception exception) {
            throw new IllegalStateException("Create token failed", exception);
        }
    }

    /**
     * 解析 JWT，并恢复当前登录用户的最小身份信息。
     */
    public AuthenticatedUser parseAuthenticatedUser(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length != 3) {
                return null;
            }
            String expectedSignature = sign(parts[0] + "." + parts[1]);
            if (!expectedSignature.equals(parts[2])) {
                return null;
            }
            Map<String, Object> payload = objectMapper.readValue(
                    Base64.getUrlDecoder().decode(parts[1]),
                    new TypeReference<>() {
                    });
            long exp = Long.parseLong(payload.get("exp").toString());
            if (Instant.now().getEpochSecond() > exp) {
                return null;
            }
            return new AuthenticatedUser(
                    Long.parseLong(payload.get("uid").toString()),
                    payload.get("sub").toString(),
                    payload.get("scope").toString());
        } catch (Exception exception) {
            return null;
        }
    }

    private String encode(Object value) throws Exception {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(objectMapper.writeValueAsBytes(value));
    }

    private String sign(String content) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(securityProperties.jwt().secret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(mac.doFinal(content.getBytes(StandardCharsets.UTF_8)));
    }
}
