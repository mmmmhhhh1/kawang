package org.example.kah.service;

import org.example.kah.security.AuthenticatedUser;

public interface AdminMobileDeviceService {

    void register(AuthenticatedUser currentUser, String vendor, String deviceToken, String deviceName);

    void unregister(AuthenticatedUser currentUser, String vendor, String deviceToken);
}
