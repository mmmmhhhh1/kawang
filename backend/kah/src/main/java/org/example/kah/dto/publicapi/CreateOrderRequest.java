package org.example.kah.dto.publicapi;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateOrderRequest(
        @NotNull(message = "商品不能为空")
        Long productId,
        @NotNull(message = "购买数量不能为空")
        @Min(value = 1, message = "购买数量最少为 1")
        @Max(value = 100, message = "购买数量最多为 100")
        Integer quantity,
        @Size(max = 200, message = "备注长度不能超过 200")
        String remark
) {
}