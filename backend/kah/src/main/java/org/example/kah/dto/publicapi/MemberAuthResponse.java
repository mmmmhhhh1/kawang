package org.example.kah.dto.publicapi;

public record MemberAuthResponse(
        String token,
        String tokenType,
        MemberProfileView profile
) {
}
