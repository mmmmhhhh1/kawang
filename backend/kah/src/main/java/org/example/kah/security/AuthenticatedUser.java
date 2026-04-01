package org.example.kah.security;

public record AuthenticatedUser(
        Long userId,
        String username,
        String scope
) {
}
