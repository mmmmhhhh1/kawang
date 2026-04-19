package org.example.kah.dto.admin;

import java.util.List;

public record AdminRuntimeMetricSectionView(
        String key,
        String title,
        List<AdminRuntimeMetricItemView> items
) {
}
