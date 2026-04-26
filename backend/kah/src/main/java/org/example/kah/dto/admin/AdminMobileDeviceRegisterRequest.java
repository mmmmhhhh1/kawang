package org.example.kah.dto.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AdminMobileDeviceRegisterRequest(
        @NotBlank @Size(max = 32) String vendor,
        @NotBlank @Size(max = 512) String deviceToken,
        @Size(max = 120) String deviceName
) {
}
