package org.example.kah.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "shop.push")
public record ShopPushProperties(Huawei huawei) {

    public record Huawei(
            boolean enabled,
            String appId,
            String clientId,
            String clientSecret,
            String tokenUrl,
            String apiBaseUrl
    ) {
    }
}
