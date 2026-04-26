package org.example.kah.service;

import org.example.kah.dto.admin.AdminNotificationEvent;
import org.example.kah.dto.admin.AdminSupportSessionItemView;
import org.example.kah.dto.support.SupportMessageView;

public interface AdminMobilePushService {

    void sendRechargeCreated(AdminNotificationEvent event);

    void sendSupportMessage(AdminSupportSessionItemView session, SupportMessageView message);
}
