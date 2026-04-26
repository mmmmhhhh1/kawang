package org.example.kah.entity;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class SupportMessage {

    private Long id;
    private Long sessionId;
    private String senderScope;
    private Long senderId;
    private String messageType;
    private String content;
    private String attachmentPath;
    private String attachmentName;
    private String attachmentContentType;
    private Long attachmentSize;
    private LocalDateTime createdAt;
}
