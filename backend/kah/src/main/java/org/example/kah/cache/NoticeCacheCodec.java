package org.example.kah.cache;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.example.kah.dto.publicapi.NoticeView;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class NoticeCacheCodec {

    private static final TypeReference<List<NoticeView>> NOTICE_LIST_TYPE = new TypeReference<>() {
    };

    private final ObjectMapper objectMapper;

    public String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new IllegalStateException("公告缓存序列化失败", exception);
        }
    }

    public List<NoticeView> parsePublishedList(String cached) {
        try {
            return objectMapper.readValue(cached, NOTICE_LIST_TYPE);
        } catch (Exception exception) {
            throw new IllegalStateException("公告缓存反序列化失败", exception);
        }
    }
}
