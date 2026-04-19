package org.example.kah.dto.admin;

import java.time.LocalDateTime;
import java.util.List;

public record AdminRuntimeDetailsView(
        LocalDateTime generatedAt,
        List<AdminRuntimeMetricSectionView> sections
) {
}
