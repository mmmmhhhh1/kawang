package org.example.kah.entity;

/**
 * 商品状态常量。
 */
public final class ProductStatus {

    /** 上架状态，可在前台展示和下单。 */
    public static final String ACTIVE = "ACTIVE";

    /** 下架状态，仅后台可见。 */
    public static final String INACTIVE = "INACTIVE";

    private ProductStatus() {
    }
}
