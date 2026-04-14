package org.example.kah.util;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

/**
 * Long 型主键工具。
 * 统一处理正数主键校验和 ID 列表归一，减少 service 中重复样板代码。
 */
public final class LongIdUtils {

    private LongIdUtils() {
    }

    /** 判断主键是否为有效正数。 */
    public static boolean isPositive(Long value) {
        return value != null && value > 0;
    }

    /** 去重并保留有效正数主键的原始顺序。 */
    public static List<Long> normalizeDistinctPositiveIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<Long> normalized = new LinkedHashSet<>();
        for (Long id : ids) {
            if (isPositive(id)) {
                normalized.add(id);
            }
        }
        return new ArrayList<>(normalized);
    }

    /** 去重并保留非空主键的原始顺序。 */
    public static List<Long> normalizeDistinctIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return new ArrayList<>(new LinkedHashSet<>(ids.stream().filter(Objects::nonNull).toList()));
    }
}
