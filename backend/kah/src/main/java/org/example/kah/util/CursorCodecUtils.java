package org.example.kah.util;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Base64;

/**
 * Encodes and decodes opaque cursor strings.
 */
public final class CursorCodecUtils {

    private CursorCodecUtils() {
    }

    public static String encode(LocalDateTime createdAt, Long id) {
        if (createdAt == null || id == null) {
            return null;
        }
        String raw = createdAt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli() + ":" + id;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes());
    }

    public static DecodedCursor decode(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return null;
        }
        try {
            String raw = new String(Base64.getUrlDecoder().decode(cursor));
            String[] parts = raw.split(":", 2);
            if (parts.length != 2) {
                return null;
            }
            long epochMilli = Long.parseLong(parts[0]);
            long id = Long.parseLong(parts[1]);
            LocalDateTime createdAt = LocalDateTime.ofInstant(
                    java.time.Instant.ofEpochMilli(epochMilli),
                    ZoneId.systemDefault());
            return new DecodedCursor(createdAt, id);
        } catch (RuntimeException exception) {
            return null;
        }
    }

    public record DecodedCursor(LocalDateTime createdAt, Long id) {
    }
}