package org.example.kah.service;

import java.util.List;
import java.util.Map;
import org.example.kah.reservation.OrderReservation;

public interface OrderReservationService {

    OrderReservation reserve(Long productId, Long userId, int quantity);

    void confirm(OrderReservation reservation);

    void rollback(OrderReservation reservation);

    void rebuildProductPool(Long productId);

    void addAvailableItems(Long productId, Map<String, Double> items);

    void removeAvailableHandles(Long productId, List<String> handles);

    void removeProductPool(Long productId);

    void backfillMissingAllocationHandles();

    void resetAndWarmupProductPools();

    void recoverExpiredReservations(int batchSize);
}