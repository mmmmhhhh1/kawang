package org.example.kah.service.impl;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Measurement;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.lang.management.RuntimeMXBean;
import java.lang.management.ThreadMXBean;
import java.sql.Connection;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import lombok.RequiredArgsConstructor;
import org.example.kah.dto.admin.AdminRuntimeDetailsView;
import org.example.kah.dto.admin.AdminRuntimeMetricItemView;
import org.example.kah.dto.admin.AdminRuntimeMetricSectionView;
import org.example.kah.dto.admin.AdminRuntimeOverviewView;
import org.example.kah.service.AdminRuntimeService;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AdminRuntimeServiceImpl implements AdminRuntimeService {

    private static final Duration OVERVIEW_TTL = Duration.ofSeconds(5);
    private static final Duration DETAILS_TTL = Duration.ofSeconds(15);

    private static final List<String> SECTION_ORDER = List.of(
            "health",
            "orders",
            "rate-limit",
            "cache",
            "balance",
            "process");

    private static final Map<String, String> SECTION_TITLES = Map.of(
            "health", "系统健康",
            "orders", "订单与预占",
            "rate-limit", "限流明细",
            "cache", "缓存",
            "balance", "余额与入账",
            "process", "JVM 与进程");

    private final DataSource dataSource;
    private final StringRedisTemplate stringRedisTemplate;
    private final MeterRegistry meterRegistry;

    private final Object overviewLock = new Object();
    private final Object healthLock = new Object();

    private final ConcurrentHashMap<String, CachedValue<AdminRuntimeMetricSectionView>> sectionCaches =
            new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Object> sectionLocks = new ConcurrentHashMap<>();

    private volatile CachedValue<AdminRuntimeOverviewView> overviewCache;
    private volatile CachedValue<HealthSnapshot> healthCache;

    @Override
    public AdminRuntimeOverviewView getOverview() {
        CachedValue<AdminRuntimeOverviewView> current = overviewCache;
        if (current != null) {
            return current.value();
        }
        return refreshOverviewSnapshot(System.currentTimeMillis()).value();
    }

    @Override
    public AdminRuntimeDetailsView getDetails(List<String> sectionKeys) {
        long now = System.currentTimeMillis();
        List<String> normalizedKeys = normalizeSectionKeys(sectionKeys);
        List<AdminRuntimeMetricSectionView> sections = normalizedKeys.stream()
                .map(key -> getSectionSnapshot(key, now))
                .map(CachedValue::value)
                .toList();
        return new AdminRuntimeDetailsView(LocalDateTime.now(), sections);
    }

    @Scheduled(initialDelay = 1000L, fixedDelay = 5000L)
    public void warmOverviewSnapshot() {
        try {
            refreshOverviewSnapshot(System.currentTimeMillis());
        } catch (Exception ignored) {
            // 保留旧快照，避免预热失败影响请求路径
        }
    }

    @Scheduled(initialDelay = 1000L, fixedDelay = 5000L)
    public void warmHealthSnapshot() {
        try {
            refreshHealthSnapshot(System.currentTimeMillis());
        } catch (Exception ignored) {
            // 保留旧快照，避免预热失败影响请求路径
        }
    }

    @Scheduled(initialDelay = 2500L, fixedDelay = 15000L)
    public void warmDetailSnapshots() {
        long now = System.currentTimeMillis();
        for (String sectionKey : SECTION_ORDER) {
            try {
                refreshSectionSnapshot(sectionKey, now);
            } catch (Exception ignored) {
                // 单个分组失败不影响其他分组
            }
        }
    }

    private CachedValue<AdminRuntimeOverviewView> refreshOverviewSnapshot(long now) {
        synchronized (overviewLock) {
            CachedValue<AdminRuntimeOverviewView> current = overviewCache;
            if (isFresh(current, now)) {
                return current;
            }
            CachedValue<AdminRuntimeOverviewView> snapshot =
                    new CachedValue<>(buildOverview(), now + OVERVIEW_TTL.toMillis());
            overviewCache = snapshot;
            return snapshot;
        }
    }

    private CachedValue<AdminRuntimeMetricSectionView> getSectionSnapshot(String sectionKey, long now) {
        CachedValue<AdminRuntimeMetricSectionView> current = sectionCaches.get(sectionKey);
        if (current != null) {
            return current;
        }
        Object lock = sectionLocks.computeIfAbsent(sectionKey, ignored -> new Object());
        synchronized (lock) {
            current = sectionCaches.get(sectionKey);
            if (current != null) {
                return current;
            }
            return refreshSectionSnapshot(sectionKey, now);
        }
    }

    private CachedValue<AdminRuntimeMetricSectionView> refreshSectionSnapshot(String sectionKey, long now) {
        CachedValue<AdminRuntimeMetricSectionView> snapshot =
                new CachedValue<>(buildSection(sectionKey), now + DETAILS_TTL.toMillis());
        sectionCaches.put(sectionKey, snapshot);
        return snapshot;
    }

    private CachedValue<HealthSnapshot> refreshHealthSnapshot(long now) {
        synchronized (healthLock) {
            CachedValue<HealthSnapshot> current = healthCache;
            if (isFresh(current, now)) {
                return current;
            }
            CachedValue<HealthSnapshot> snapshot =
                    new CachedValue<>(buildHealthSnapshot(), now + OVERVIEW_TTL.toMillis());
            healthCache = snapshot;
            return snapshot;
        }
    }

    private boolean isFresh(CachedValue<?> value, long now) {
        return value != null && value.expiresAtMillis() > now;
    }

    private List<String> normalizeSectionKeys(List<String> sectionKeys) {
        if (sectionKeys == null || sectionKeys.isEmpty()) {
            return SECTION_ORDER;
        }
        LinkedHashSet<String> ordered = new LinkedHashSet<>();
        for (String key : sectionKeys) {
            if (key == null || key.isBlank()) {
                continue;
            }
            Arrays.stream(key.split(","))
                    .map(String::trim)
                    .filter(SECTION_TITLES::containsKey)
                    .forEach(ordered::add);
        }
        if (ordered.isEmpty()) {
            return SECTION_ORDER;
        }
        return SECTION_ORDER.stream().filter(ordered::contains).toList();
    }

    private AdminRuntimeOverviewView buildOverview() {
        HealthSnapshot healthSnapshot = getLightweightHealthSnapshot();
        AdminRuntimeOverviewView.Health health = new AdminRuntimeOverviewView.Health(
                healthSnapshot.serviceStatus(),
                healthSnapshot.databaseStatus(),
                healthSnapshot.redisStatus());

        double reservationSuccess = counter("shop.order.reservation.events", "result", "success");
        double reservationFailure = counter("shop.order.reservation.events", "result", "failure");
        double reservationRollback = counter("shop.order.reservation.events", "result", "rollback");
        double reservationRecover = counter("shop.order.reservation.events", "result", "recover");
        double orderSuccess = counter("shop.order.transaction.count", "result", "success");
        double orderFailure = counter("shop.order.transaction.count", "result", "failure");
        double averageSuccessDurationMs = timerMeanMillis("shop.order.transaction.duration", "result", "success");

        double allowedTotal = sumMeters("shop.rate.limit.requests", "result", "allowed");
        double blockedTotal = sumMeters("shop.rate.limit.requests", "result", "blocked");

        double productBaseHit = counter("shop.cache.product.base.requests", "result", "hit");
        double productBaseMiss = counter("shop.cache.product.base.requests", "result", "miss");
        double productStatsHit = counter("shop.cache.product.stats.requests", "result", "hit");
        double productStatsMiss = counter("shop.cache.product.stats.requests", "result", "miss");
        double noticeHit = counter("shop.cache.notice.requests", "result", "hit");
        double noticeMiss = counter("shop.cache.notice.requests", "result", "miss");

        AdminRuntimeOverviewView.Cache cache = new AdminRuntimeOverviewView.Cache(
                rate(productBaseHit, productBaseMiss),
                rate(productStatsHit, productStatsMiss),
                rate(noticeHit, noticeMiss));

        AdminRuntimeOverviewView.Balance balance = new AdminRuntimeOverviewView.Balance(
                counter("shop.balance.debit.events", "result", "success"),
                counter("shop.balance.debit.events", "result", "conflict"),
                counter("shop.balance.credit.events", "biz", "recharge", "result", "duplicate"),
                counter("shop.balance.credit.events", "biz", "refund", "result", "duplicate"));

        RuntimeMXBean runtimeMXBean = ManagementFactory.getRuntimeMXBean();
        MemoryMXBean memoryMXBean = ManagementFactory.getMemoryMXBean();
        MemoryUsage heap = memoryMXBean.getHeapMemoryUsage();
        long heapUsed = Math.max(heap.getUsed(), 0L);
        long heapMax = heap.getMax() > 0 ? heap.getMax() : Runtime.getRuntime().maxMemory();
        AdminRuntimeOverviewView.Process process = new AdminRuntimeOverviewView.Process(
                runtimeMXBean.getUptime() / 1000d,
                gaugePercent("system.cpu.usage"),
                gaugePercent("process.cpu.usage"),
                heapUsed,
                heapMax,
                heapMax > 0 ? (heapUsed * 100d) / heapMax : 0d);

        AdminRuntimeOverviewView.Orders orders = new AdminRuntimeOverviewView.Orders(
                reservationSuccess,
                reservationFailure,
                reservationRollback,
                reservationRecover,
                orderSuccess,
                orderFailure,
                averageSuccessDurationMs);

        AdminRuntimeOverviewView.RateLimit rateLimit =
                new AdminRuntimeOverviewView.RateLimit(allowedTotal, blockedTotal);
        return new AdminRuntimeOverviewView(LocalDateTime.now(), health, orders, rateLimit, cache, balance, process);
    }

    private AdminRuntimeMetricSectionView buildSection(String sectionKey) {
        return switch (sectionKey) {
            case "health" -> buildHealthSection();
            case "orders" -> buildOrderSection();
            case "rate-limit" -> buildRateLimitSection();
            case "cache" -> buildCacheSection();
            case "balance" -> buildBalanceSection();
            case "process" -> buildProcessSection();
            default -> new AdminRuntimeMetricSectionView(
                    sectionKey,
                    Objects.requireNonNullElse(SECTION_TITLES.get(sectionKey), sectionKey),
                    List.of());
        };
    }

    private AdminRuntimeMetricSectionView buildHealthSection() {
        HealthSnapshot snapshot = getLightweightHealthSnapshot();
        return new AdminRuntimeMetricSectionView(
                "health",
                SECTION_TITLES.get("health"),
                List.of(
                        metric("health.service", "服务状态", snapshot.serviceStatus()),
                        metric("health.database", "MySQL 状态", snapshot.databaseStatus()),
                        metric("health.redis", "Redis 状态", snapshot.redisStatus())));
    }

    private AdminRuntimeMetricSectionView buildOrderSection() {
        return new AdminRuntimeMetricSectionView(
                "orders",
                SECTION_TITLES.get("orders"),
                List.of(
                        metric("reservation.success", "预占成功", formatCount(counter("shop.order.reservation.events", "result", "success")) + " 次"),
                        metric("reservation.failure", "预占失败", formatCount(counter("shop.order.reservation.events", "result", "failure")) + " 次"),
                        metric("reservation.rollback", "预占回滚", formatCount(counter("shop.order.reservation.events", "result", "rollback")) + " 次"),
                        metric("reservation.recover", "预占恢复", formatCount(counter("shop.order.reservation.events", "result", "recover")) + " 次"),
                        metric("order.success", "下单成功", formatCount(counter("shop.order.transaction.count", "result", "success")) + " 次"),
                        metric("order.failure", "下单失败", formatCount(counter("shop.order.transaction.count", "result", "failure")) + " 次"),
                        metric("order.duration.avg", "下单平均耗时", formatDuration(timerMeanMillis("shop.order.transaction.duration", "result", "success"))),
                        metric("order.duration.max", "下单最大耗时", formatDuration(timerMaxMillis("shop.order.transaction.duration", "result", "success")))));
    }

    private AdminRuntimeMetricSectionView buildRateLimitSection() {
        List<AdminRuntimeMetricItemView> items = new ArrayList<>();
        appendEndpointCounters(items, "allowed", "放行");
        appendEndpointCounters(items, "blocked", "拦截");
        items.sort(Comparator.comparing(AdminRuntimeMetricItemView::label));
        return new AdminRuntimeMetricSectionView("rate-limit", SECTION_TITLES.get("rate-limit"), items);
    }

    private void appendEndpointCounters(List<AdminRuntimeMetricItemView> items, String result, String labelSuffix) {
        meterRegistry.find("shop.rate.limit.requests").tag("result", result).meters().stream()
                .sorted(Comparator.comparing(meter -> Objects.requireNonNullElse(meter.getId().getTag("endpoint"), "")))
                .forEach(meter -> {
                    String endpoint = Objects.requireNonNullElse(meter.getId().getTag("endpoint"), "unknown");
                    double count = counterValue(meter);
                    if (count <= 0d) {
                        return;
                    }
                    items.add(metric(
                            "rate-limit." + result + "." + endpoint,
                            endpoint + " / " + labelSuffix,
                            formatCount(count) + " 次"));
                });
    }

    private AdminRuntimeMetricSectionView buildCacheSection() {
        double productBaseHit = counter("shop.cache.product.base.requests", "result", "hit");
        double productBaseMiss = counter("shop.cache.product.base.requests", "result", "miss");
        double productStatsHit = counter("shop.cache.product.stats.requests", "result", "hit");
        double productStatsMiss = counter("shop.cache.product.stats.requests", "result", "miss");
        double noticeHit = counter("shop.cache.notice.requests", "result", "hit");
        double noticeMiss = counter("shop.cache.notice.requests", "result", "miss");

        return new AdminRuntimeMetricSectionView(
                "cache",
                SECTION_TITLES.get("cache"),
                List.of(
                        metric("cache.product-base.hit", "商品基础命中", formatCount(productBaseHit) + " 次"),
                        metric("cache.product-base.miss", "商品基础未命中", formatCount(productBaseMiss) + " 次"),
                        metric("cache.product-base.rebuild", "商品基础重建", formatCount(counter("shop.cache.product.base.requests", "result", "rebuild")) + " 次"),
                        metric("cache.product-base.fallback", "商品基础回源", formatCount(counter("shop.cache.product.base.requests", "result", "fallback")) + " 次"),
                        metric("cache.product-base.rate", "商品基础命中率", formatPercent(rate(productBaseHit, productBaseMiss))),
                        metric("cache.product-stats.hit", "商品统计命中", formatCount(productStatsHit) + " 次"),
                        metric("cache.product-stats.miss", "商品统计未命中", formatCount(productStatsMiss) + " 次"),
                        metric("cache.product-stats.rebuild", "商品统计重建", formatCount(counter("shop.cache.product.stats.requests", "result", "rebuild")) + " 次"),
                        metric("cache.product-stats.fallback", "商品统计回源", formatCount(counter("shop.cache.product.stats.requests", "result", "fallback")) + " 次"),
                        metric("cache.product-stats.rate", "商品统计命中率", formatPercent(rate(productStatsHit, productStatsMiss))),
                        metric("cache.notice.hit", "公告命中", formatCount(noticeHit) + " 次"),
                        metric("cache.notice.miss", "公告未命中", formatCount(noticeMiss) + " 次"),
                        metric("cache.notice.rebuild", "公告重建", formatCount(counter("shop.cache.notice.requests", "result", "rebuild")) + " 次"),
                        metric("cache.notice.fallback", "公告回源", formatCount(counter("shop.cache.notice.requests", "result", "fallback")) + " 次"),
                        metric("cache.notice.rate", "公告命中率", formatPercent(rate(noticeHit, noticeMiss)))));
    }

    private AdminRuntimeMetricSectionView buildBalanceSection() {
        return new AdminRuntimeMetricSectionView(
                "balance",
                SECTION_TITLES.get("balance"),
                List.of(
                        metric("balance.debit.success", "扣款成功", formatCount(counter("shop.balance.debit.events", "result", "success")) + " 次"),
                        metric("balance.debit.conflict", "扣款冲突", formatCount(counter("shop.balance.debit.events", "result", "conflict")) + " 次"),
                        metric("balance.recharge.duplicate", "充值重复入账", formatCount(counter("shop.balance.credit.events", "biz", "recharge", "result", "duplicate")) + " 次"),
                        metric("balance.refund.duplicate", "退款重复入账", formatCount(counter("shop.balance.credit.events", "biz", "refund", "result", "duplicate")) + " 次")));
    }

    private AdminRuntimeMetricSectionView buildProcessSection() {
        RuntimeMXBean runtimeMXBean = ManagementFactory.getRuntimeMXBean();
        ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();
        MemoryMXBean memoryMXBean = ManagementFactory.getMemoryMXBean();
        MemoryUsage heap = memoryMXBean.getHeapMemoryUsage();
        MemoryUsage nonHeap = memoryMXBean.getNonHeapMemoryUsage();
        long heapUsed = Math.max(heap.getUsed(), 0L);
        long heapMax = heap.getMax() > 0 ? heap.getMax() : Runtime.getRuntime().maxMemory();
        double heapPercent = heapMax > 0 ? (heapUsed * 100d) / heapMax : 0d;
        double systemCpu = gaugePercent("system.cpu.usage");
        double processCpu = gaugePercent("process.cpu.usage");

        return new AdminRuntimeMetricSectionView(
                "process",
                SECTION_TITLES.get("process"),
                List.of(
                        metric("process.uptime", "运行时长", formatSeconds(runtimeMXBean.getUptime() / 1000d)),
                        metric("process.cpu.system", "系统 CPU", formatPercent(systemCpu)),
                        metric("process.cpu.app", "进程 CPU", formatPercent(processCpu)),
                        metric("process.heap.used", "堆内存已用", formatBytes(heapUsed)),
                        metric("process.heap.max", "堆内存上限", formatBytes(heapMax)),
                        metric("process.heap.rate", "堆内存占用", formatPercent(heapPercent)),
                        metric("process.non-heap.used", "非堆已用", formatBytes(nonHeap.getUsed())),
                        metric("process.threads.live", "活动线程", formatCount(threadMXBean.getThreadCount()) + " 条")));
    }

    private HealthSnapshot getLightweightHealthSnapshot() {
        CachedValue<HealthSnapshot> current = healthCache;
        if (current != null) {
            return current.value();
        }
        return refreshHealthSnapshot(System.currentTimeMillis()).value();
    }

    private HealthSnapshot buildHealthSnapshot() {
        String databaseStatus = probeDatabaseStatus();
        String redisStatus = probeRedisStatus();
        String serviceStatus = ("UP".equals(databaseStatus) || "UP".equals(redisStatus)) ? "UP" : "DEGRADED";
        if ("UP".equals(databaseStatus) && "UP".equals(redisStatus)) {
            serviceStatus = "UP";
        }
        if (!"UP".equals(databaseStatus) && !"UP".equals(redisStatus)) {
            serviceStatus = "DOWN";
        }
        return new HealthSnapshot(serviceStatus, databaseStatus, redisStatus);
    }

    private String probeDatabaseStatus() {
        try (Connection connection = dataSource.getConnection()) {
            return connection.isValid(1) ? "UP" : "DOWN";
        } catch (Exception ignored) {
            return "DOWN";
        }
    }

    private String probeRedisStatus() {
        try {
            String pong = stringRedisTemplate.execute((RedisConnection connection) -> connection.ping());
            return "PONG".equalsIgnoreCase(pong) ? "UP" : "DOWN";
        } catch (Exception ignored) {
            return "DOWN";
        }
    }

    private double counter(String name, String... tags) {
        return meterRegistry.find(name).tags(tags).counters().stream().mapToDouble(Counter::count).sum();
    }

    private double sumMeters(String name, String... tags) {
        return meterRegistry.find(name).tags(tags).meters().stream().mapToDouble(this::counterValue).sum();
    }

    private double counterValue(Meter meter) {
        double total = 0d;
        for (Measurement measurement : meter.measure()) {
            if ("COUNT".equals(measurement.getStatistic().name())) {
                total += measurement.getValue();
            }
        }
        return total;
    }

    private double timerMeanMillis(String name, String... tags) {
        Timer timer = meterRegistry.find(name).tags(tags).timer();
        if (timer == null || timer.count() == 0) {
            return 0d;
        }
        return timer.mean(TimeUnit.MILLISECONDS);
    }

    private double timerMaxMillis(String name, String... tags) {
        Timer timer = meterRegistry.find(name).tags(tags).timer();
        if (timer == null || timer.count() == 0) {
            return 0d;
        }
        return timer.max(TimeUnit.MILLISECONDS);
    }

    private double gaugePercent(String name) {
        Gauge gauge = meterRegistry.find(name).gauge();
        if (gauge == null) {
            return 0d;
        }
        return Math.max(gauge.value() * 100d, 0d);
    }

    private double rate(double hit, double miss) {
        double total = hit + miss;
        if (total <= 0d) {
            return 0d;
        }
        return (hit * 100d) / total;
    }

    private AdminRuntimeMetricItemView metric(String key, String label, String formattedValue) {
        return new AdminRuntimeMetricItemView(key, label, formattedValue);
    }

    private String formatCount(double value) {
        return Long.toString(Math.round(value));
    }

    private String formatDuration(double millis) {
        return millis <= 0d ? "0 ms" : String.format("%.1f ms", millis);
    }

    private String formatPercent(double percent) {
        return String.format("%.1f%%", percent);
    }

    private String formatSeconds(double seconds) {
        if (seconds < 60d) {
            return formatCount(seconds) + " 秒";
        }
        if (seconds < 3600d) {
            return String.format("%.1f 分钟", seconds / 60d);
        }
        return String.format("%.1f 小时", seconds / 3600d);
    }

    private String formatBytes(long bytes) {
        if (bytes <= 0L) {
            return "0 B";
        }
        double value = bytes;
        String[] units = {"B", "KB", "MB", "GB", "TB"};
        int index = 0;
        while (value >= 1024 && index < units.length - 1) {
            value /= 1024d;
            index += 1;
        }
        return String.format(index == 0 ? "%.0f %s" : "%.1f %s", value, units[index]);
    }

    private record CachedValue<T>(T value, long expiresAtMillis) {
    }

    private record HealthSnapshot(String serviceStatus, String databaseStatus, String redisStatus) {
    }
}
