package org.example.kah.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * Frontend member entity.
 */
@Data
public class MemberUser {

    private Long id;
    private String mail;
    private String username;
    private String passwordHash;
    private String status;
    private BigDecimal balance;
    private LocalDateTime lastLoginAt;
    private LocalDateTime lastSeenAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}