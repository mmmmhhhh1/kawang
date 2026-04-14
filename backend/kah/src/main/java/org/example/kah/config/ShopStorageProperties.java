package org.example.kah.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "shop.storage")
public record ShopStorageProperties(
        String basePath
) {
    public ShopStorageProperties {
        if (basePath == null || basePath.isBlank()) {
            basePath = "./storage";
        }
    }
}