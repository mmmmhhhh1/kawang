package org.example.kah.reservation;

import java.util.List;

public record OrderReservation(String token, Long productId, Long userId, List<OrderReservationItem> items) {
}