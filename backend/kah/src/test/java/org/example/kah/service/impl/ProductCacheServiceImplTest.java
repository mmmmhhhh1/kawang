package org.example.kah.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.example.kah.cache.ProductCacheCodec;
import org.example.kah.cache.ProductCacheConstants;
import org.example.kah.cache.ProductStatsCacheItem;
import org.example.kah.entity.ProductStatus;
import org.example.kah.entity.ShopProduct;
import org.example.kah.mapper.ProductMapper;
import org.example.kah.metrics.ShopMetricsService;
import org.example.kah.service.DistributedLockService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
class ProductCacheServiceImplTest {

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private ProductCacheCodec productCacheCodec;

    @Mock
    private ProductMapper productMapper;

    @Mock
    private DistributedLockService distributedLockService;

    @Mock
    private ShopMetricsService shopMetricsService;

    @BeforeEach
    void setUp() {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    void refreshProductBaseUpdatesDetailAndEvictsListCache() {
        ProductCacheServiceImpl service = new ProductCacheServiceImpl(
                stringRedisTemplate,
                productCacheCodec,
                productMapper,
                distributedLockService,
                shopMetricsService);
        ShopProduct product = new ShopProduct();
        product.setId(11L);
        product.setSku("sku-11");
        product.setTitle("product");
        product.setVendor("vendor");
        product.setPlanName("plan");
        product.setDescription("desc");
        product.setPrice(new BigDecimal("9.90"));
        product.setStatus(ProductStatus.ACTIVE);
        product.setSortOrder(1);
        when(productMapper.findById(11L)).thenReturn(product);
        when(productCacheCodec.toJson(any())).thenReturn("{}");

        service.refreshProductBase(11L);

        verify(valueOperations).set(eq(ProductCacheConstants.baseDetailKey(11L)), eq("{}"));
        verify(stringRedisTemplate).delete(List.of(ProductCacheConstants.ACTIVE_PRODUCT_BASE_LIST_KEY));
    }

    @Test
    void getProductStatsLoadsMissingEntriesInSingleBatch() {
        ProductCacheServiceImpl service = new ProductCacheServiceImpl(
                stringRedisTemplate,
                productCacheCodec,
                productMapper,
                distributedLockService,
                shopMetricsService);
        ProductStatsCacheItem cachedItem = new ProductStatsCacheItem(1L, 10, 5);
        ProductStatsCacheItem loadedItem = new ProductStatsCacheItem(2L, 8, 3);
        when(valueOperations.multiGet(List.of(
                        ProductCacheConstants.statsKey(1L),
                        ProductCacheConstants.statsKey(2L),
                        ProductCacheConstants.statsKey(3L))))
                .thenReturn(Arrays.asList("{cached}", null, null));
        when(productCacheCodec.parseStats("{cached}")).thenReturn(cachedItem);
        when(productMapper.findStatsByIds(List.of(2L, 3L))).thenReturn(List.of(loadedItem));
        when(productCacheCodec.toJson(loadedItem)).thenReturn("{loaded}");

        Map<Long, ProductStatsCacheItem> result = service.getProductStats(List.of(1L, 2L, 3L));
        Map<Long, ProductStatsCacheItem> expected = new LinkedHashMap<>();
        expected.put(1L, cachedItem);
        expected.put(2L, loadedItem);

        assertEquals(expected, result);
        verify(productMapper).findStatsByIds(List.of(2L, 3L));
        verifyNoMoreInteractions(productMapper);
        verify(shopMetricsService).recordProductStatsCacheHit(1);
        verify(shopMetricsService).recordProductStatsCacheMiss(2);
        verify(shopMetricsService).recordProductStatsCacheRebuild(2);
        verify(valueOperations).set(eq(ProductCacheConstants.statsKey(2L)), eq("{loaded}"), any(Duration.class));
        verify(valueOperations).set(
                eq(ProductCacheConstants.statsKey(3L)),
                eq(ProductCacheConstants.NULL_MARKER),
                any(Duration.class));
        verify(distributedLockService, never()).tryAcquire(any(), any(), any());
    }
}
