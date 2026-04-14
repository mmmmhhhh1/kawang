package org.example.kah.cache;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 商品缓存编解码器。
 * 把 JSON 序列化、反序列化以及空值占位规则集中起来，避免缓存服务里混入大量编解码细节。
 */
@Component
@RequiredArgsConstructor
public class ProductCacheCodec {

    private static final TypeReference<List<ProductBaseCacheItem>> BASE_LIST_TYPE = new TypeReference<>() {
    };

    private final ObjectMapper objectMapper;

    /** 将对象序列化为 JSON 字符串。 */
    public String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new IllegalStateException("商品缓存序列化失败", exception);
        }
    }

    /** 将缓存字符串解析为商品基础信息列表。 */
    public List<ProductBaseCacheItem> parseBaseList(String cached) {
        if (ProductCacheConstants.NULL_MARKER.equals(cached)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(cached, BASE_LIST_TYPE);
        } catch (Exception exception) {
            throw new IllegalStateException("商品基础列表缓存反序列化失败", exception);
        }
    }

    /** 将缓存字符串解析为单商品基础信息。 */
    public ProductBaseCacheItem parseBaseDetail(String cached) {
        if (ProductCacheConstants.NULL_MARKER.equals(cached)) {
            return null;
        }
        try {
            return objectMapper.readValue(cached, ProductBaseCacheItem.class);
        } catch (Exception exception) {
            throw new IllegalStateException("商品基础详情缓存反序列化失败", exception);
        }
    }

    /** 将缓存字符串解析为单商品统计信息。 */
    public ProductStatsCacheItem parseStats(String cached) {
        if (ProductCacheConstants.NULL_MARKER.equals(cached)) {
            return null;
        }
        try {
            return objectMapper.readValue(cached, ProductStatsCacheItem.class);
        } catch (Exception exception) {
            throw new IllegalStateException("商品统计缓存反序列化失败", exception);
        }
    }
}
