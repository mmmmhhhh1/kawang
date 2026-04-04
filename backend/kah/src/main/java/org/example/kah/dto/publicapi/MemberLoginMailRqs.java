package org.example.kah.dto.publicapi;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record MemberLoginMailRqs(
        @Email
        String email,
        @NotBlank
        String code
) {
}
