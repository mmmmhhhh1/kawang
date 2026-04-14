package org.example.kah.dto.admin;

import jakarta.validation.constraints.Size;

public record AdminRechargeDecisionRequest(
        @Size(max = 200, message = "拒绝原因长度不能超过 200")
        String reason
) {
}