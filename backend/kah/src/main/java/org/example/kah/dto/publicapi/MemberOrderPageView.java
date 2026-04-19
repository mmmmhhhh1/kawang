package org.example.kah.dto.publicapi;

import org.example.kah.common.CursorPageResponse;

public record MemberOrderPageView(
        MemberOrderSummaryView summary,
        CursorPageResponse<OrderQueryView> page
) {
}
