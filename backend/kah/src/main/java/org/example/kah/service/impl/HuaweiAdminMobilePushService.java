package org.example.kah.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.example.kah.config.ShopPushProperties;
import org.example.kah.dto.admin.AdminNotificationEvent;
import org.example.kah.dto.admin.AdminSupportSessionItemView;
import org.example.kah.dto.support.SupportMessageView;
import org.example.kah.mapper.AdminMobileDeviceMapper;
import org.example.kah.service.AdminMobilePushService;
import org.example.kah.support.AsyncTaskSupport;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class HuaweiAdminMobilePushService implements AdminMobilePushService {

    private static final int TOKEN_BATCH_SIZE = 200;

    private final ShopPushProperties shopPushProperties;
    private final AdminMobileDeviceMapper adminMobileDeviceMapper;
    private final ObjectMapper objectMapper;
    private final AsyncTaskSupport asyncTaskSupport;
    private final HttpClient httpClient = HttpClient.newBuilder().build();

    private volatile String cachedAccessToken;
    private volatile Instant cachedAccessTokenExpiresAt;

    @Override
    public void sendRechargeCreated(AdminNotificationEvent event) {
        String title = "收到新的充值审核";
        asyncTaskSupport.runAsync(() -> sendNotification(
                true,
                title,
                event.message(),
                Map.of(
                        "kind", "recharge",
                        "title", title,
                        "message", event.message(),
                        "requestId", event.requestId() == null ? "" : String.valueOf(event.requestId()),
                        "route", event.requestId() == null ? "recharges" : "recharges/" + event.requestId())));
    }

    @Override
    public void sendSupportMessage(AdminSupportSessionItemView session, SupportMessageView message) {
        String displayName = session.memberUsername() != null && !session.memberUsername().isBlank()
                ? session.memberUsername()
                : (session.memberEmail() != null && !session.memberEmail().isBlank() ? session.memberEmail() : "会员");
        String title = "新的客服消息";
        String body = message.content().length() <= 96 ? message.content() : message.content().substring(0, 96);
        asyncTaskSupport.runAsync(() -> sendNotification(
                false,
                title,
                "来自 " + displayName + "：" + body,
                Map.of(
                        "kind", "support",
                        "title", title,
                        "message", "来自 " + displayName + "：" + body,
                        "sessionId", String.valueOf(session.id()),
                        "memberId", String.valueOf(session.memberId()),
                        "route", "support/" + session.id())));
    }

    private void sendNotification(boolean superAdminOnly, String title, String body, Map<String, String> payloadData) {
        ShopPushProperties.Huawei huawei = shopPushProperties.huawei();
        if (huawei == null || !huawei.enabled()) {
            return;
        }
        if (isBlank(huawei.appId()) || isBlank(huawei.clientId()) || isBlank(huawei.clientSecret())) {
            return;
        }
        List<String> tokens = superAdminOnly
                ? adminMobileDeviceMapper.findEnabledTokensForSuperAdmins("HUAWEI")
                : adminMobileDeviceMapper.findEnabledTokensForActiveAdmins("HUAWEI");
        if (tokens == null || tokens.isEmpty()) {
            return;
        }
        String accessToken = getAccessToken(huawei);
        if (accessToken == null || accessToken.isBlank()) {
            return;
        }

        for (List<String> batch : partition(tokens, TOKEN_BATCH_SIZE)) {
            postMessage(huawei, accessToken, batch, title, body, payloadData);
        }
    }

    private String getAccessToken(ShopPushProperties.Huawei huawei) {
        Instant now = Instant.now();
        if (cachedAccessToken != null && cachedAccessTokenExpiresAt != null
                && now.isBefore(cachedAccessTokenExpiresAt.minusSeconds(30))) {
            return cachedAccessToken;
        }
        synchronized (this) {
            if (cachedAccessToken != null && cachedAccessTokenExpiresAt != null
                    && now.isBefore(cachedAccessTokenExpiresAt.minusSeconds(30))) {
                return cachedAccessToken;
            }
            try {
                String body = "grant_type=client_credentials"
                        + "&client_id=" + urlEncode(huawei.clientId())
                        + "&client_secret=" + urlEncode(huawei.clientSecret());
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(defaultIfBlank(huawei.tokenUrl(), "https://oauth-login.cloud.huawei.com/oauth2/v3/token")))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .build();
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    return null;
                }
                JsonNode json = objectMapper.readTree(response.body());
                String token = json.path("access_token").asText(null);
                long expiresIn = json.path("expires_in").asLong(0);
                if (token == null || token.isBlank()) {
                    return null;
                }
                cachedAccessToken = token;
                cachedAccessTokenExpiresAt = Instant.now().plusSeconds(Math.max(60, expiresIn));
                return cachedAccessToken;
            } catch (IOException exception) {
                return null;
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                return null;
            }
        }
    }

    private void postMessage(
            ShopPushProperties.Huawei huawei,
            String accessToken,
            List<String> tokens,
            String title,
            String body,
            Map<String, String> payloadData) {
        try {
            Map<String, Object> envelope = new LinkedHashMap<>();
            envelope.put("validate_only", false);

            Map<String, Object> message = new LinkedHashMap<>();
            message.put("token", tokens);
            message.put("notification", Map.of("title", title, "body", body));
            message.put("data", objectMapper.writeValueAsString(payloadData));
            message.put("android", Map.of(
                    "notification", Map.of(
                            "title", title,
                            "body", body,
                            "click_action", Map.of(
                                    "type", 1,
                                    "intent", "#Intent;scheme=kawang;package=org.example.kawang.adminmobile;S.route="
                                            + payloadData.getOrDefault("route", "") + ";end"))));
            envelope.put("message", message);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(defaultIfBlank(huawei.apiBaseUrl(), "https://push-api.cloud.huawei.com")
                            + "/v1/" + huawei.appId() + "/messages:send"))
                    .header("Content-Type", "application/json;charset=UTF-8")
                    .header("Authorization", "Bearer " + accessToken)
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(envelope)))
                    .build();
            httpClient.send(request, HttpResponse.BodyHandlers.discarding());
        } catch (IOException ignored) {
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    private List<List<String>> partition(List<String> source, int size) {
        List<List<String>> batches = new ArrayList<>();
        for (int index = 0; index < source.size(); index += size) {
            batches.add(source.subList(index, Math.min(source.size(), index + size)));
        }
        return batches;
    }

    private String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String defaultIfBlank(String value, String fallback) {
        return isBlank(value) ? fallback : value;
    }
}
