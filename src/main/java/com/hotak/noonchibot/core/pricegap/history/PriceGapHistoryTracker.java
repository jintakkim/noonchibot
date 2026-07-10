package com.hotak.noonchibot.core.pricegap.history;

import com.hotak.noonchibot.connector.PriceCandleDataSource;
import com.hotak.noonchibot.core.Exchange;
import com.hotak.noonchibot.core.IoExecutor;
import com.hotak.noonchibot.core.LifecycleAware;
import com.hotak.noonchibot.core.pricegap.PriceGapFeedDefinition;
import com.hotak.noonchibot.core.pricegap.PriceGapSubscriptionKey;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.TaskScheduler;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
public class PriceGapHistoryTracker implements LifecycleAware {
    private static final BigDecimal BPS = new BigDecimal("10000");
    private static final Duration REFRESH_INTERVAL = Duration.ofMinutes(1);

    private final List<PriceGapFeedDefinition> definitions;
    private final Map<Exchange, PriceCandleDataSource> dataSources;
    private final TaskScheduler taskScheduler;
    private final IoExecutor ioExecutor;
    private final Map<PriceGapSubscriptionKey, Map<PriceGapTimeline, List<PriceGapHistoryPoint>>> histories =
            new ConcurrentHashMap<>();
    private final AtomicBoolean refreshing = new AtomicBoolean();
    private volatile boolean running;
    private volatile ScheduledFuture<?> refreshTask;

    public PriceGapHistoryTracker(
            List<PriceGapFeedDefinition> definitions,
            Map<Exchange, PriceCandleDataSource> dataSources,
            TaskScheduler taskScheduler,
            IoExecutor ioExecutor
    ) {
        this.definitions = List.copyOf(definitions);
        this.dataSources = Map.copyOf(dataSources);
        this.taskScheduler = taskScheduler;
        this.ioExecutor = ioExecutor;
    }

    public Optional<PriceGapHistory> history(String tradingPair, PriceGapTimeline timeline) {
        Map<PriceGapTimeline, List<PriceGapHistoryPoint>> byTimeline =
                histories.get(new PriceGapSubscriptionKey(tradingPair));
        if (byTimeline == null) return Optional.empty();
        List<PriceGapHistoryPoint> points = byTimeline.get(timeline);
        if (points == null) return Optional.empty();
        return Optional.of(new PriceGapHistory(tradingPair, timeline, points));
    }

    @Override
    public void onStart() {
        running = true;
        refresh();
        refreshTask = taskScheduler.scheduleWithFixedDelay(this::refresh, REFRESH_INTERVAL);
    }

    @Override
    public void onShutdown() {
        running = false;
        ScheduledFuture<?> task = refreshTask;
        refreshTask = null;
        if (task != null) task.cancel(false);
    }

    private void refresh() {
        if (!running || !refreshing.compareAndSet(false, true)) return;
        ioExecutor.execute(() -> {
            try {
                for (PriceGapFeedDefinition definition : definitions) {
                    for (PriceGapTimeline timeline : PriceGapTimeline.values()) {
                        try {
                            refresh(definition, timeline);
                        } catch (Exception e) {
                            log.warn("Price gap history refresh failed: pair={}, timeline={}",
                                    definition.key().tradingPair(), timeline, e);
                        }
                    }
                }
            } finally {
                refreshing.set(false);
            }
        });
    }

    private void refresh(PriceGapFeedDefinition definition, PriceGapTimeline timeline) {
        Instant closedBefore = floor(Instant.now(), timeline.bucketSize());
        List<PriceGapHistoryPoint> current = history(definition.key().tradingPair(), timeline)
                .map(PriceGapHistory::points)
                .orElse(List.of());
        Instant from = current.isEmpty()
                ? closedBefore.minus(timeline.window())
                : current.getLast().timestamp();

        Map<Exchange, Map<Instant, BigDecimal>> closes = new EnumMap<>(Exchange.class);
        definition.sourceTradingPairs().forEach((exchange, pair) -> closes.put(
                exchange,
                fetch(exchange, pair, timeline.bucketSize(), from, closedBefore)
        ));
        Map<Instant, BigDecimal> stableRates = definition.stableRateExchange() == null
                ? Map.of()
                : fetch(definition.stableRateExchange(), definition.stableRateTradingPair(),
                        timeline.bucketSize(), from, closedBefore);

        NavigableMap<Instant, PriceGapHistoryPoint> merged = new TreeMap<>();
        current.forEach(point -> merged.put(point.timestamp(), point));
        closes.values().stream()
                .flatMap(values -> values.keySet().stream())
                .distinct()
                .sorted()
                .forEach(timestamp -> calculate(definition, timestamp, closes, stableRates)
                        .ifPresent(point -> merged.put(timestamp, point)));

        Instant cutoff = closedBefore.minus(timeline.window());
        merged.headMap(cutoff, false).clear();
        put(definition.key(), timeline, List.copyOf(merged.values()));
    }

    private Map<Instant, BigDecimal> fetch(
            Exchange exchange,
            String pair,
            Duration interval,
            Instant from,
            Instant to
    ) {
        PriceCandleDataSource source = dataSources.get(exchange);
        if (source == null) throw new IllegalStateException("Price candle data source not found: " + exchange);
        Map<Instant, BigDecimal> result = new HashMap<>();
        source.fetch(pair, interval, from, to).stream()
                .filter(candle -> candle.timestamp().isBefore(to))
                .forEach(candle -> result.put(candle.timestamp(), candle.close()));
        return result;
    }

    private Optional<PriceGapHistoryPoint> calculate(
            PriceGapFeedDefinition definition,
            Instant timestamp,
            Map<Exchange, Map<Instant, BigDecimal>> closes,
            Map<Instant, BigDecimal> stableRates
    ) {
        String targetQuote = quote(definition.key().tradingPair());
        List<NormalizedClose> normalized = new ArrayList<>();
        definition.sourceTradingPairs().forEach((exchange, pair) -> {
            BigDecimal close = closes.getOrDefault(exchange, Map.of()).get(timestamp);
            if (close == null) return;
            String sourceQuote = quote(pair);
            if (sourceQuote.equals(targetQuote)) {
                normalized.add(new NormalizedClose(exchange, close));
            } else if (definition.stableRateTradingPair() != null) {
                BigDecimal stableRate = stableRates.get(timestamp);
                if (stableRate != null && definition.stableRateTradingPair().equals(sourceQuote + "-" + targetQuote)) {
                    normalized.add(new NormalizedClose(exchange, close.multiply(stableRate)));
                }
            }
        });
        if (normalized.size() < 2) return Optional.empty();
        normalized.sort(Comparator.comparing(NormalizedClose::price));
        BigDecimal reference = median(normalized.stream().map(NormalizedClose::price).toList());
        NormalizedClose lowest = normalized.getFirst();
        NormalizedClose highest = normalized.getLast();
        BigDecimal gapRate = highest.price().subtract(lowest.price())
                .divide(reference, MathContext.DECIMAL64);
        return Optional.of(new PriceGapHistoryPoint(
                timestamp,
                lowest.exchange(),
                highest.exchange(),
                gapRate,
                gapRate.multiply(BPS)
        ));
    }

    private void put(
            PriceGapSubscriptionKey key,
            PriceGapTimeline timeline,
            List<PriceGapHistoryPoint> points
    ) {
        histories.compute(key, (ignored, existing) -> {
            EnumMap<PriceGapTimeline, List<PriceGapHistoryPoint>> copy = new EnumMap<>(PriceGapTimeline.class);
            if (existing != null) copy.putAll(existing);
            copy.put(timeline, points);
            return Map.copyOf(copy);
        });
    }

    private Instant floor(Instant instant, Duration interval) {
        long millis = interval.toMillis();
        return Instant.ofEpochMilli(Math.floorDiv(instant.toEpochMilli(), millis) * millis);
    }

    private BigDecimal median(List<BigDecimal> values) {
        int middle = values.size() / 2;
        if (values.size() % 2 == 1) return values.get(middle);
        return values.get(middle - 1).add(values.get(middle)).divide(BigDecimal.TWO);
    }

    private String quote(String tradingPair) {
        int separator = tradingPair.lastIndexOf('-');
        if (separator < 0) throw new IllegalArgumentException("Invalid trading pair: " + tradingPair);
        return tradingPair.substring(separator + 1);
    }

    private record NormalizedClose(Exchange exchange, BigDecimal price) {}
}
