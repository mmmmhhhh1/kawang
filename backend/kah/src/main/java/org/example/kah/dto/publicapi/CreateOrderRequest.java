package org.example.kah.dto.publicapi;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateOrderRequest(
        @NotNull(message = "商品不能为空")
        Long productId,
        @NotNull(message = "购买数量不能为空")
        @Min(value = 1, message = "购买数量最少为 1")
        @Max(value = 10, message = "购买数量最多为 10")
        Integer quantity,
        @NotBlank(message = "买家姓名不能为空")
        @Size(max = 32, message = "买家姓名长度不能超过 32")
        String buyerName,
        @NotBlank(message = "联系方式不能为空")
        @Size(max = 64, message = "联系方式长度不能超过 64")
        String buyerContact,
        @NotBlank(message = "查单密码不能为空")
        @Size(min = 6, max = 20, message = "查单密码长度需在 6 到 20 之间")
        @Pattern(regexp = "^[A-Za-z0-9]+$", message = "查单密码只能包含字母和数字")
        String lookupSecret,
        @Size(max = 200, message = "备注长度不能超过 200")
        String remark
) {
}
