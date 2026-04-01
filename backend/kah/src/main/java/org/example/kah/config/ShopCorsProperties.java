package org.example.kah.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "shop.cors")
public record ShopCorsProperties(List<String> allowedOrigins) {
}
