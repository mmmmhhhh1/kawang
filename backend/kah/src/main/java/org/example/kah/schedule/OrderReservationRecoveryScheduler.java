package org.example.kah.schedule;

import lombok.RequiredArgsConstructor;
import org.example.kah.common.OrderReservationConstants;
import org.example.kah.service.OrderReservationService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OrderReservationRecoveryScheduler {

    private final OrderReservationService orderReservationService;

    @Scheduled(initialDelay = 30000L, fixedDelay = 15000L)
    public void recoverExpiredReservations() {
        orderReservationService.recoverExpiredReservations(OrderReservationConstants.ORDER_RESERVATION_RECOVERY_BATCH_SIZE);
    }
}