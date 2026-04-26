package org.example.kah.entity;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class AdminMobileDevice {

    private Long id;
    private Long adminUserId;
    private String vendor;
    private String deviceToken;
    private String deviceName;
    private Boolean pushEnabled;
    private LocalDateTime lastSeenAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
