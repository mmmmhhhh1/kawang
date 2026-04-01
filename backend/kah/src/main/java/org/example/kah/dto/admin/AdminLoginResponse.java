package org.example.kah.dto.admin;

public record AdminLoginResponse(
        String token,
        String tokenType,
        AdminProfileResponse profile
) {
}
