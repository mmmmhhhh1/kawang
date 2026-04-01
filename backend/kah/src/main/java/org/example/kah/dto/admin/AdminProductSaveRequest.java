package org.example.kah.dto.admin;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record AdminProductSaveRequest(
        @NotBlank(message = "SKU 不能为空")
        @Size(max = 64, message = "SKU 长度不能超过 64")
        String sku,
        @NotBlank(message = "商品名称不能为空")
        @Size(max = 120, message = "商品名称长度不能超过 120")
        String title,
        @NotBlank(message = "厂商不能为空")
        @Size(max = 80, message = "厂商长度不能超过 80")
        String vendor,
        @NotBlank(message = "套餐名称不能为空")
        @Size(max = 80, message = "套餐名称长度不能超过 80")
        String planName,
        @NotBlank(message = "商品描述不能为空")
        @Size(max = 500, message = "商品描述长度不能超过 500")
        String description,
        @NotNull(message = "价格不能为空")
        @DecimalMin(value = "0.01", message = "价格必须大于 0")
        BigDecimal price,
        @NotBlank(message = "状态不能为空")
        String status,
        @NotNull(message = "排序不能为空")
        @Max(value = 9999, message = "排序值过大")
        Integer sortOrder
) {
}
