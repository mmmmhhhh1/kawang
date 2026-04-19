package org.example.kah.service.impl;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.example.kah.common.BusinessException;
import org.example.kah.common.ErrorCode;
import org.example.kah.common.OrderReservationConstants;
import org.example.kah.entity.EnableStatus;
import org.example.kah.entity.ProductAccount;
import org.example.kah.entity.SaleStatus;
import org.example.kah.mapper.ProductAccountMapper;
import org.example.kah.metrics.ShopMetricsService;
import org.example.kah.reservation.OrderReservation;
import org.example.kah.reservation.OrderReservationItem;
import org.example.kah.service.OrderReservationService;
import org.example.kah.util.AllocationHandleGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class OrderReservationServiceImpl implements OrderReservationService {

    private static final Logger log = LoggerFactory.getLogger(OrderReservationServiceImpl.class);
    private static final int KEY_SCAN_BATCH_SIZE = 200;

    private static final DefaultRedisScript<List> RESERVE_SCRIPT = loadListScript("lua/order_reservation_reserve.lua");
    private static final DefaultRedisScript<Long> CLEANUP_SCRIPT = loadLongScript("lua/order_reservation_cleanup.lua");
    private static final DefaultRedisScript<Long> SWAP_POOL_SCRIPT = loadLongScript("lua/order_pool_swap.lua");
    private static final DefaultRedisScript<Long> ADD_AVAILABLE_ITEMS_SCRIPT = loadLongScript("lua/order_pool_add_available.lua");

    private final StringRedisTemplate stringRedisTemplate;
    private final ProductAccountMapper productAccountMapper;
    private final ShopMetricsService shopMetricsService;

    @Override
    public OrderReservation reserve(Long productId, Long userId, int quantity) {
        requirePositive(productId, "商品不存在");
        requirePositive(userId, "用户不存在");
        if (quantity < 1) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "购买数量至少为 1");
        }

        String token = UUID.randomUUID().toString().replace("-", "");
        long expiresAt = Instant.now().plus(OrderReservationConstants.ORDER_RESERVATION_TTL).toEpochMilli();
        List<?> raw;
        try {
            raw = executeReserveScript(productId, userId, quantity, token, expiresAt);
        } catch (RuntimeException | Error exception) {
            shopMetricsService.recordReservationFailure();
            throw exception;
        }
        List<OrderReservationItem> items = toReservationItems(raw);
        if (items.size() < quantity) {
            cleanupTokenOnly(token);
            shopMetricsService.recordReservationFailure();
            throw new BusinessException(ErrorCode.BAD_REQUEST, "库存不足，请稍后再试");
        }
        shopMetricsService.recordReservationSuccess();
        return new OrderReservation(token, productId, userId, items);
    }

    @Override
    public void confirm(OrderReservation reservation) {
        if (reservation == null) {
            return;
        }
        try {
            cleanupReservation(reservation, List.of());
        } catch (Exception exception) {
            log.warn("订单已提交，但清理预占资源失败，token={}", reservation.token(), exception);
        }
    }

    @Override
    public void rollback(OrderReservation reservation) {
        if (reservation == null) {
            return;
        }
        try {
            List<OrderReservationItem> restorableItems = resolveRestorableItems(reservation.productId(), reservation.items());
            cleanupReservation(reservation, restorableItems);
        } catch (Exception exception) {
            log.warn("回滚预占资源失败，准备重建商品资源池，productId={}, token={}", reservation.productId(), reservation.token(), exception);
            rebuildProductPool(reservation.productId());
        }
        shopMetricsService.recordReservationRollback();
    }

    @Override
    public void rebuildProductPool(Long productId) {
        if (productId == null || productId <= 0) {
            return;
        }
        String poolKey = OrderReservationConstants.availablePoolKey(productId);
        String reservedKey = OrderReservationConstants.reservedPoolKey(productId);
        String tempKey = poolKey + ":rebuild:" + UUID.randomUUID();
        try {
            List<ProductAccount> reservableAccounts = productAccountMapper.findReservableByProductId(productId);
            Set<String> reservedHandles = stringRedisTemplate.opsForZSet().range(reservedKey, 0, -1);
            Map<String, Double> tuples = new LinkedHashMap<>();
            for (ProductAccount account : reservableAccounts) {
                if (account.getAllocationHandle() == null || account.getAllocationHandle().isBlank()) {
                    continue;
                }
                if (reservedHandles != null && reservedHandles.contains(account.getAllocationHandle())) {
                    continue;
                }
                tuples.put(account.getAllocationHandle(), account.getId().doubleValue());
            }
            stringRedisTemplate.delete(tempKey);
            if (!tuples.isEmpty()) {
                stringRedisTemplate.opsForZSet().add(tempKey, convertTuples(tuples));
            }
            stringRedisTemplate.execute(SWAP_POOL_SCRIPT, List.of(tempKey, poolKey));
        } catch (Exception exception) {
            stringRedisTemplate.delete(tempKey);
            log.warn("重建商品资源池失败，productId={}", productId, exception);
        }
    }

    @Override
    public void addAvailableItems(Long productId, Map<String, Double> items) {
        if (productId == null || productId <= 0 || items == null || items.isEmpty()) {
            return;
        }
        try {
            List<String> args = new ArrayList<>();
            for (Map.Entry<String, Double> entry : items.entrySet()) {
                if (entry.getKey() == null || entry.getKey().isBlank() || entry.getValue() == null) {
                    continue;
                }
                args.add(entry.getKey());
                args.add(String.valueOf(entry.getValue()));
            }
            if (args.isEmpty()) {
                return;
            }
            stringRedisTemplate.execute(
                    ADD_AVAILABLE_ITEMS_SCRIPT,
                    List.of(
                            OrderReservationConstants.availablePoolKey(productId),
                            OrderReservationConstants.reservedPoolKey(productId)),
                    args.toArray(String[]::new));
        } catch (Exception exception) {
            log.warn("增量写入商品资源池失败，productId={}", productId, exception);
            rebuildProductPool(productId);
        }
    }

    @Override
    public void removeAvailableHandles(Long productId, List<String> handles) {
        if (productId == null || productId <= 0 || handles == null || handles.isEmpty()) {
            return;
        }
        List<String> normalizedHandles = handles.stream()
                .filter(handle -> handle != null && !handle.isBlank())
                .distinct()
                .toList();
        if (normalizedHandles.isEmpty()) {
            return;
        }
        try {
            stringRedisTemplate.opsForZSet().remove(
                    OrderReservationConstants.availablePoolKey(productId),
                    normalizedHandles.toArray(String[]::new));
        } catch (Exception exception) {
            log.warn("增量删除商品资源池失败，productId={}", productId, exception);
            rebuildProductPool(productId);
        }
    }

    @Override
    public void removeProductPool(Long productId) {
        if (productId == null || productId <= 0) {
            return;
        }
        stringRedisTemplate.delete(List.of(
                OrderReservationConstants.availablePoolKey(productId),
                OrderReservationConstants.reservedPoolKey(productId)));
    }

    @Override
    public void backfillMissingAllocationHandles() {
        while (true) {
            List<ProductAccount> rows = productAccountMapper.findMissingAllocationHandles(200);
            if (rows.isEmpty()) {
                return;
            }
            for (ProductAccount row : rows) {
                boolean updated = false;
                for (int attempt = 0; attempt < 5 && !updated; attempt++) {
                    String handle = AllocationHandleGenerator.newHandle();
                    try {
                        updated = productAccountMapper.updateAllocationHandle(row.getId(), handle) > 0;
                    } catch (Exception exception) {
                        log.debug("补齐卡密分配句柄时命中重复值，id={}", row.getId(), exception);
                    }
                }
                if (!updated) {
                    throw new IllegalStateException("补齐卡密分配句柄失败，id=" + row.getId());
                }
            }
        }
    }

    @Override
    public void resetAndWarmupProductPools() {
        clearReservationKeys();
        for (Long productId : productAccountMapper.findAllCardKeyProductIds()) {
            rebuildProductPool(productId);
        }
    }

    @Override
    public void recoverExpiredReservations(int batchSize) {
        int safeBatchSize = Math.max(batchSize, 1);
        long now = Instant.now().toEpochMilli();
        Set<String> tokens = stringRedisTemplate.opsForZSet()
                .rangeByScore(OrderReservationConstants.ORDER_RESERVATION_EXPIRE_INDEX_KEY, 0, now, 0, safeBatchSize);
        if (tokens == null || tokens.isEmpty()) {
            return;
        }
        for (String token : tokens) {
            try {
                OrderReservation reservation = loadReservation(token);
                if (reservation == null) {
                    cleanupTokenOnly(token);
                    continue;
                }
                List<OrderReservationItem> restorableItems = resolveRestorableItems(reservation.productId(), reservation.items());
                cleanupReservation(reservation, restorableItems);
                shopMetricsService.recordReservationRecover();
            } catch (Exception exception) {
                log.warn("恢复过期预占资源失败，token={}", token, exception);
            }
        }
    }

    private List<?> executeReserveScript(Long productId, Long userId, int quantity, String token, long expiresAt) {
        try {
            return stringRedisTemplate.execute(
                    RESERVE_SCRIPT,
                    List.of(
                            OrderReservationConstants.availablePoolKey(productId),
                            OrderReservationConstants.reservationItemsKey(token),
                            OrderReservationConstants.reservationMetaKey(token),
                            OrderReservationConstants.ORDER_RESERVATION_EXPIRE_INDEX_KEY,
                            OrderReservationConstants.reservedPoolKey(productId)),
                    String.valueOf(quantity),
                    String.valueOf(expiresAt),
                    String.valueOf(OrderReservationConstants.ORDER_RESERVATION_TTL.toMillis()),
                    String.valueOf(productId),
                    String.valueOf(userId),
                    token);
        } catch (Exception exception) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "下单服务暂时不可用，请稍后重试");
        }
    }

    private void cleanupReservation(OrderReservation reservation, List<OrderReservationItem> restoreItems) {
        List<String> args = new ArrayList<>();
        args.add(String.valueOf(restoreItems.size()));
        for (OrderReservationItem item : restoreItems) {
            args.add(item.handle());
            args.add(String.valueOf(item.score()));
        }
        args.add(reservation.token());
        stringRedisTemplate.execute(
                CLEANUP_SCRIPT,
                List.of(
                        OrderReservationConstants.availablePoolKey(reservation.productId()),
                        OrderReservationConstants.reservationItemsKey(reservation.token()),
                        OrderReservationConstants.reservationMetaKey(reservation.token()),
                        OrderReservationConstants.ORDER_RESERVATION_EXPIRE_INDEX_KEY,
                        OrderReservationConstants.reservedPoolKey(reservation.productId())),
                args.toArray(String[]::new));
    }

    private OrderReservation loadReservation(String token) {
        Map<Object, Object> meta = stringRedisTemplate.opsForHash().entries(OrderReservationConstants.reservationMetaKey(token));
        if (meta == null || meta.isEmpty()) {
            return null;
        }
        Object productIdValue = meta.get("productId");
        Object userIdValue = meta.get("userId");
        if (productIdValue == null || userIdValue == null) {
            return null;
        }

        Set<ZSetOperations.TypedTuple<String>> tuples = stringRedisTemplate.opsForZSet()
                .rangeWithScores(OrderReservationConstants.reservationItemsKey(token), 0, -1);
        List<OrderReservationItem> items = new ArrayList<>();
        if (tuples != null) {
            for (ZSetOperations.TypedTuple<String> tuple : tuples) {
                if (tuple.getValue() == null || tuple.getScore() == null) {
                    continue;
                }
                items.add(new OrderReservationItem(tuple.getValue(), tuple.getScore()));
            }
        }

        return new OrderReservation(
                token,
                Long.valueOf(String.valueOf(productIdValue)),
                Long.valueOf(String.valueOf(userIdValue)),
                items);
    }

    private List<OrderReservationItem> resolveRestorableItems(Long productId, List<OrderReservationItem> items) {
        if (items.isEmpty()) {
            return List.of();
        }
        List<String> handles = items.stream().map(OrderReservationItem::handle).toList();
        Map<String, ProductAccount> accountMap = productAccountMapper.findByAllocationHandles(productId, handles).stream()
                .collect(LinkedHashMap::new, (map, account) -> map.put(account.getAllocationHandle(), account), Map::putAll);
        List<OrderReservationItem> restorable = new ArrayList<>();
        for (OrderReservationItem item : items) {
            ProductAccount account = accountMap.get(item.handle());
            if (account == null) {
                continue;
            }
            boolean available = SaleStatus.UNSOLD.equals(account.getSaleStatus())
                    && EnableStatus.ENABLED.equals(account.getEnableStatus())
                    && account.getAssignedOrderId() == null;
            if (available) {
                restorable.add(item);
            }
        }
        return restorable;
    }

    private List<OrderReservationItem> toReservationItems(List<?> raw) {
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        List<OrderReservationItem> items = new ArrayList<>();
        for (int index = 0; index + 1 < raw.size(); index += 2) {
            items.add(new OrderReservationItem(
                    String.valueOf(raw.get(index)),
                    Double.parseDouble(String.valueOf(raw.get(index + 1)))));
        }
        return items;
    }

    private void cleanupTokenOnly(String token) {
        stringRedisTemplate.delete(List.of(
                OrderReservationConstants.reservationItemsKey(token),
                OrderReservationConstants.reservationMetaKey(token)));
        stringRedisTemplate.opsForZSet().remove(OrderReservationConstants.ORDER_RESERVATION_EXPIRE_INDEX_KEY, token);
    }

    private void clearReservationKeys() {
        try {
            deleteKeysByPattern(OrderReservationConstants.ORDER_RESERVATION_PREFIX + "*");
        } catch (Exception exception) {
            log.warn("清理预占资源缓存失败", exception);
        }
    }

    private void deleteKeysByPattern(String pattern) {
        stringRedisTemplate.execute((RedisCallback<Void>) connection -> {
            ScanOptions scanOptions = ScanOptions.scanOptions().match(pattern).count(KEY_SCAN_BATCH_SIZE).build();
            try (Cursor<byte[]> cursor = connection.scan(scanOptions)) {
                List<byte[]> batch = new ArrayList<>();
                while (cursor.hasNext()) {
                    batch.add(cursor.next());
                    if (batch.size() >= KEY_SCAN_BATCH_SIZE) {
                        deleteRawKeys(connection, batch);
                    }
                }
                deleteRawKeys(connection, batch);
            } catch (Exception exception) {
                throw new IllegalStateException("扫描并删除 Redis 键失败", exception);
            }
            return null;
        });
    }

    private void deleteRawKeys(RedisConnection connection, List<byte[]> batch) {
        if (batch.isEmpty()) {
            return;
        }
        connection.del(batch.toArray(byte[][]::new));
        batch.clear();
    }

    private Set<ZSetOperations.TypedTuple<String>> convertTuples(Map<String, Double> tuples) {
        Set<ZSetOperations.TypedTuple<String>> typedTuples = new LinkedHashSet<>();
        for (Map.Entry<String, Double> entry : tuples.entrySet()) {
            typedTuples.add(ZSetOperations.TypedTuple.of(entry.getKey(), entry.getValue()));
        }
        return typedTuples;
    }

    private void requirePositive(Long value, String message) {
        if (value == null || value <= 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, message);
        }
    }

    private static DefaultRedisScript<List> loadListScript(String location) {
        DefaultRedisScript<List> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource(location));
        script.setResultType(List.class);
        return script;
    }

    private static DefaultRedisScript<Long> loadLongScript(String location) {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource(location));
        script.setResultType(Long.class);
        return script;
    }
}