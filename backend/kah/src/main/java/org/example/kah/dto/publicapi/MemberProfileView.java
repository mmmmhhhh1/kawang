package org.example.kah.dto.publicapi;

import java.math.BigDecimal;

public record MemberProfileView(
        Long id,
        String username,
        String email,
        BigDecimal balance
) {
}