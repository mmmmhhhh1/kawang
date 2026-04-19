package org.example.kah.metrics;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Service;

@Service
public class ShopMetricsService {

    private final MeterRegistry meterRegistry;

    private final Counter reservationSuccessCounter;
    private final Counter reservationFailureCounter;
    private final Counter reservationRollbackCounter;
    private final Counter reservationRecoverCounter;

    private final Counter orderSuccessCounter;
    private final Counter orderFailureCounter;
    private final Timer orderSuccessTimer;
    private final Timer orderFailureTimer;

    private final Counter productBaseHitCounter;
    private final Counter productBaseMissCounter;
    private final Counter productBaseRebuildCounter;
    private final Counter productBaseFallbackCounter;
    private final Counter productStatsHitCounter;
    private final Counter productStatsMissCounter;
    private final Counter productStatsRebuildCounter;
    private final Counter productStatsFallbackCounter;
    private final Counter noticeHitCounter;
    private final Counter noticeMissCounter;
    private final Counter noticeRebuildCounter;
    private final Counter noticeFallbackCounter;

    private final Counter balanceDebitSuccessCounter;
    private final Counter balanceDebitConflictCounter;
    private final Counter rechargeDuplicateCounter;
    private final Counter refundDuplicateCounter;

    private final Map<String, Counter> rateLimitAllowedCounters = new ConcurrentHashMap<>();
    private final Map<String, Counter> rateLimitBlockedCounters = new ConcurrentHashMap<>();

    public ShopMetricsService(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        this.reservationSuccessCounter = counter("shop.order.reservation.events", "result", "success");
        this.reservationFailureCounter = counter("shop.order.reservation.events", "result", "failure");
        this.reservationRollbackCounter = counter("shop.order.reservation.events", "result", "rollback");
        this.reservationRecoverCounter = counter("shop.order.reservation.events", "result", "recover");

        this.orderSuccessCounter = counter("shop.order.transaction.count", "result", "success");
        this.orderFailureCounter = counter("shop.order.transaction.count", "result", "failure");
        this.orderSuccessTimer = timer("shop.order.transaction.duration", "result", "success");
        this.orderFailureTimer = timer("shop.order.transaction.duration", "result", "failure");

        this.productBaseHitCounter = counter("shop.cache.product.base.requests", "result", "hit");
        this.productBaseMissCounter = counter("shop.cache.product.base.requests", "result", "miss");
        this.productBaseRebuildCounter = counter("shop.cache.product.base.requests", "result", "rebuild");
        this.productBaseFallbackCounter = counter("shop.cache.product.base.requests", "result", "fallback");
        this.productStatsHitCounter = counter("shop.cache.product.stats.requests", "result", "hit");
        this.productStatsMissCounter = counter("shop.cache.product.stats.requests", "result", "miss");
        this.productStatsRebuildCounter = counter("shop.cache.product.stats.requests", "result", "rebuild");
        this.productStatsFallbackCounter = counter("shop.cache.product.stats.requests", "result", "fallback");
        this.noticeHitCounter = counter("shop.cache.notice.requests", "result", "hit");
        this.noticeMissCounter = counter("shop.cache.notice.requests", "result", "miss");
        this.noticeRebuildCounter = counter("shop.cache.notice.requests", "result", "rebuild");
        this.noticeFallbackCounter = counter("shop.cache.notice.requests", "result", "fallback");

        this.balanceDebitSuccessCounter = counter("shop.balance.debit.events", "result", "success");
        this.balanceDebitConflictCounter = counter("shop.balance.debit.events", "result", "conflict");
        this.rechargeDuplicateCounter = counter("shop.balance.credit.events", "biz", "recharge", "result", "duplicate");
        this.refundDuplicateCounter = counter("shop.balance.credit.events", "biz", "refund", "result", "duplicate");
    }

    public void recordReservationSuccess() {
        reservationSuccessCounter.increment();
    }

    public void recordReservationFailure() {
        reservationFailureCounter.increment();
    }

    public void recordReservationRollback() {
        reservationRollbackCounter.increment();
    }

    public void recordReservationRecover() {
        reservationRecoverCounter.increment();
    }

    public void recordOrderTransactionSuccess(Duration duration) {
        orderSuccessCounter.increment();
        orderSuccessTimer.record(duration);
    }

    public void recordOrderTransactionFailure(Duration duration) {
        orderFailureCounter.increment();
        orderFailureTimer.record(duration);
    }

    public void recordRateLimitAllowed(String endpoint) {
        rateLimitAllowedCounters
                .computeIfAbsent(endpoint, key -> counter("shop.rate.limit.requests", "endpoint", key, "result", "allowed"))
                .increment();
    }

    public void recordRateLimitBlocked(String endpoint) {
        rateLimitBlockedCounters
                .computeIfAbsent(endpoint, key -> counter("shop.rate.limit.requests", "endpoint", key, "result", "blocked"))
                .increment();
    }

    public void recordProductBaseCacheHit() {
        productBaseHitCounter.increment();
    }

    public void recordProductBaseCacheMiss() {
        productBaseMissCounter.increment();
    }

    public void recordProductBaseCacheRebuild() {
        productBaseRebuildCounter.increment();
    }

    public void recordProductBaseCacheFallback() {
        productBaseFallbackCounter.increment();
    }

    public void recordProductStatsCacheHit(double count) {
        productStatsHitCounter.increment(count);
    }

    public void recordProductStatsCacheMiss(double count) {
        productStatsMissCounter.increment(count);
    }

    public void recordProductStatsCacheRebuild() {
        recordProductStatsCacheRebuild(1);
    }

    public void recordProductStatsCacheRebuild(double count) {
        productStatsRebuildCounter.increment(count);
    }

    public void recordProductStatsCacheFallback(double count) {
        productStatsFallbackCounter.increment(count);
    }

    public void recordNoticeCacheHit() {
        noticeHitCounter.increment();
    }

    public void recordNoticeCacheMiss() {
        noticeMissCounter.increment();
    }

    public void recordNoticeCacheRebuild() {
        noticeRebuildCounter.increment();
    }

    public void recordNoticeCacheFallback() {
        noticeFallbackCounter.increment();
    }

    public void recordBalanceDebitSuccess() {
        balanceDebitSuccessCounter.increment();
    }

    public void recordBalanceDebitConflict() {
        balanceDebitConflictCounter.increment();
    }

    public void recordRechargeDuplicate() {
        rechargeDuplicateCounter.increment();
    }

    public void recordRefundDuplicate() {
        refundDuplicateCounter.increment();
    }

    private Counter counter(String name, String... tags) {
        return Counter.builder(name).tags(tags).register(meterRegistry);
    }

    private Timer timer(String name, String... tags) {
        return Timer.builder(name).tags(tags).register(meterRegistry);
    }
}
