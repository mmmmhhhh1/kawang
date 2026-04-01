package org.example.kah.dto.admin;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
//用recod自动生成get set tostring hash
public record AdminAccountCreateRequest(
        @NotNull(message = "商品不能为空")
        Long productId,
        @Valid
        @NotEmpty(message = "账号列表不能为空")
        List<AdminAccountItemRequest> items
) {
}
