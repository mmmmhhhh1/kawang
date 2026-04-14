package org.example.kah.util;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * {@link LocalDateTime} 与字符串之间的编解码工具。
 * 统一使用 ISO_LOCAL_DATE_TIME，便于 Redis 与数据库回退逻辑复用。
 */
public final class LocalDateTimeCodecUtils {

    /** 统一的日期时间格式。 */
    public static final DateTimeFormatter ISO_LOCAL_DATE_TIME = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private LocalDateTimeCodecUtils() {
    }

    /** 将时间格式化为字符串。 */
    public static String format(LocalDateTime value) {
        return value == null ? null : value.format(ISO_LOCAL_DATE_TIME);
    }

    /**
     * 将对象安全解析为时间。
     * Redis hash 读出来的是 Object，这里统一做兼容解析。
     */
    public static LocalDateTime parse(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return LocalDateTime.parse(String.valueOf(value), ISO_LOCAL_DATE_TIME);
        } catch (DateTimeParseException exception) {
            return null;
        }
    }
}
