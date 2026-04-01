package org.example.kah.dto.publicapi;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record QueryOrdersRequest(
        @NotBlank(message = "联系方式不能为空")
        @Size(max = 64, message = "联系方式长度不能超过 64")
        String buyerContact,
        @Size(max = 64, message = "订单号长度不能超过 64")
        String orderNo
) {
}
