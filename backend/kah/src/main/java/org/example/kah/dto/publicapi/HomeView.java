package org.example.kah.dto.publicapi;

import java.util.List;

public record HomeView(List<ProductView> products, List<NoticeView> notices) {
}
