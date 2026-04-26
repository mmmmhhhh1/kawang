package org.example.kah.entity;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class SupportSession {

    private Long id;
    private Long memberId;
    private String status;
    private String lastMessagePreview;
    private LocalDateTime lastMessageAt;
    private Integer memberUnreadCount;
    private Integer adminUnreadCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    private String memberUsername;
    private String memberEmail;
    private LocalDateTime sortTime;
}
