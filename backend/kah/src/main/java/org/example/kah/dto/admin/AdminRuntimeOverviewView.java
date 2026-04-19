package org.example.kah.dto.admin;

import java.time.LocalDateTime;

public record AdminRuntimeOverviewView(
        LocalDateTime generatedAt,
        Health health,
        Orders orders,
        RateLimit rateLimit,
        Cache cache,
        Balance balance,
        Process process
) {

    public record Health(
            String serviceStatus,
            String databaseStatus,
            String redisStatus
    ) {
    }

    public record Orders(
            double reservationSuccess,
            double reservationFailure,
            double reservationRollback,
            double reservationRecover,
            double orderSuccess,
            double orderFailure,
            double averageSuccessDurationMs
    ) {
    }

    public record RateLimit(
            double allowedTotal,
            double blockedTotal
    ) {
    }

    public record Cache(
            double productBaseHitRate,
            double productStatsHitRate,
            double noticeHitRate
    ) {
    }

    public record Balance(
            double debitSuccess,
            double debitConflict,
            double rechargeDuplicate,
            double refundDuplicate
    ) {
    }

    public record Process(
            double uptimeSeconds,
            double systemCpuUsage,
            double processCpuUsage,
            long heapUsedBytes,
            long heapMaxBytes,
            double heapUsagePercent
    ) {
    }
}
