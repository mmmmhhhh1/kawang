package org.example.kah.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "shop.crypto")
public record ShopCryptoProperties(String aesKey) {
}
