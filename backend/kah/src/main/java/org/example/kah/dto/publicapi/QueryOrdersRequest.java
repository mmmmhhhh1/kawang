package org.example.kah.dto.publicapi;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record QueryOrdersRequest(
        @NotBlank(message = "联系方式不能为空")
        @Size(max = 64, message = "联系方式长度不能超过 64")
        String buyerContact,
        @Size(min = 6, max = 20, message = "查单密码长度需在 6 到 20 之间")
        @Pattern(regexp = "^[A-Za-z0-9]+$", message = "查单密码只能包含字母和数字")
        String lookupSecret,
        @Size(max = 64, message = "订单号长度不能超过 64")
        String orderNo
) {
}
