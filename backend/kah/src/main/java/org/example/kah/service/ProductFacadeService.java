package org.example.kah.service;

import java.util.List;
import org.example.kah.dto.publicapi.ProductView;

/**
 * 前台商品读取服务接口。
 * 负责公开商品列表和单个商品详情输出。
 */
public interface ProductFacadeService {

    /**
     * 查询前台可售商品列表。
     *
     * @return 商品视图集合
     */
    List<ProductView> listProducts();

    /**
     * 查询单个商品详情。
     *
     * @param id 商品主键
     * @return 商品视图
     */
    ProductView getProduct(Long id);
}
