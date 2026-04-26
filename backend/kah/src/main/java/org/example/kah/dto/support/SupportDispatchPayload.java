package org.example.kah.dto.support;

import org.example.kah.dto.admin.AdminSupportSessionItemView;
import org.example.kah.dto.publicapi.MemberSupportSessionView;

public record SupportDispatchPayload(
        SupportMessageView message,
        AdminSupportSessionItemView adminSession,
        MemberSupportSessionView memberSession,
        SupportUnreadView unread
) {
}
