package org.example.kah.entity;

import java.time.LocalDateTime;
import lombok.Data;

/**
 * 商品账号池实体。
 * 对应表 {@code product_account}，保存商品可分配账号的脱敏信息与密文。
 */
@Data
public class ProductAccount {

    /** 账号记录主键。 */
    private Long id;

    /** 所属商品主键。 */
    private Long productId;

    /** 仅供展示的商品标题，来源于关联查询，不直接落表。 */
    private String productTitle;

    /** 脱敏后的账号名，用于后台列表和订单快照展示。 */
    private String accountNameMasked;

    /** 加密后的账号名密文。 */
    private String accountCiphertext;

    /** 加密后的账号密码或密钥密文。 */
    private String secretCiphertext;

    /** 加密后的补充备注密文。 */
    private String noteCiphertext;

    /** 账号摘要，用于防重复校验。 */
    private String accountDigest;

    /** 账号状态，例如 AVAILABLE / ASSIGNED / DISABLED。 */
    private String status;

    /** 已分配到的订单主键。 */
    private Long assignedOrderId;

    /** 最近一次分配时间。 */
    private LocalDateTime assignedAt;

    /** 创建时间。 */
    private LocalDateTime createdAt;

    /** 最后更新时间。 */
    private LocalDateTime updatedAt;
}
