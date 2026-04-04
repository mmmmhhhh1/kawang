package org.example.kah.dto.publicapi;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record CodeSend(
        @Email
        String email,
        @NotBlank
        String scene

) {
}
