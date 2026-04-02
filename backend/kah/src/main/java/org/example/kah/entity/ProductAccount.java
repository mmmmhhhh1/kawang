package org.example.kah.entity;

import java.time.LocalDateTime;
import lombok.Data;

/**
 * 商品资源池实体。
 * 对应表 {@code product_account}，当前主要承载卡密池记录，同时兼容历史账号池迁移数据。
 */
@Data
public class ProductAccount {

    /** 资源记录主键。 */
    private Long id;

    /** 所属商品主键。 */
    private Long productId;

    /** 联表查询时带出的商品标题。 */
    private String productTitle;

    /** 旧账号池兼容字段，用于旧表结构的非空约束和历史数据保留。 */
    private String accountNameMasked;

    /** 旧账号池兼容字段。 */
    private String accountCiphertext;

    /** 旧账号池兼容字段。 */
    private String secretCiphertext;

    /** 通用备注密文。 */
    private String noteCiphertext;

    /** 旧账号池兼容摘要字段。 */
    private String accountDigest;

    /** 旧单状态兼容字段，不再作为卡密池主业务状态来源。 */
    private String status;

    /** 资源类型，区分卡密和历史账号。 */
    private String resourceType;

    /** 卡密正文密文。 */
    private String cardKeyCiphertext;

    /** 卡密摘要，用于同商品内防重复导入。 */
    private String cardKeyDigest;

    /** 售卖状态，表示是否已经卖出。 */
    private String saleStatus;

    /** 启用状态，表示当前卡密是否允许参与销售。 */
    private String enableStatus;

    /** 已分配到的订单主键。 */
    private Long assignedOrderId;

    /** 最近一次分配时间。 */
    private LocalDateTime assignedAt;

    /** 创建时间。 */
    private LocalDateTime createdAt;

    /** 最后更新时间。 */
    private LocalDateTime updatedAt;
}