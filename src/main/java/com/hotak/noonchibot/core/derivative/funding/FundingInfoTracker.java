package com.hotak.noonchibot.core.derivative.funding;

import com.google.common.annotations.VisibleForTesting;
import com.hotak.noonchibot.connector.FundingRateHistoryDataSource;
import com.hotak.noonchibot.connector.TradingPairSymbolRegistry;
import com.hotak.noonchibot.core.LifecycleAware;
import com.hotak.noonchibot.core.Exchange;
import com.hotak.noonchibot.core.config.Phases;
import com.hotak.noonchibot.core.event.EventPublisher;
import com.hotak.noonchibot.core.event.EventSubscriber;
import com.hotak.noonchibot.core.event.ExecutionPolicy;
import com.hotak.noonchibot.core.event.Subscription;
import com.hotak.noonchibot.core.event.internal.derivative.FundingInfoEvent;
import com.hotak.noonchibot.core.event.internal.ExchangeUnavailableEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.concurrent.SimpleAsyncTaskScheduler;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

@Slf4j
public class FundingInfoTracker implements LifecycleAware {
    private static final Duration CONFIRMED_FUNDING_TIME_TOLERANCE = Duration.ofSeconds(1);

    private final Duration defaultFundingInterval;
    private final Exchange exchange;
    private final Map<String, FundingInfo> fundingInfos = new HashMap<>();
    private volatile Map<String, NavigableMap<Instant, FundingRatePoint>> confirmedHistory = Map.of();
    private final String fundingCoin;
    private final TradingPairSymbolRegistry tradingPairSymbolRegistry;
    private final EventSubscriber eventSubscriber;
    private final EventPublisher eventPublisher;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final Set<Subscription> subscriptions = new HashSet<>();

    private final FundingRateHistoryDataSource historyDataSource;
    private final TaskScheduler taskScheduler;
    private final FundingHistoryProperties historyProperties;
    private final TimeWeightedMovingAverage movingAverage = new TimeWeightedMovingAverage();
    private final Map<SettlementKey, ScheduledFuture<?>> settlementTasks = new ConcurrentHashMap<>();
    private final Set<String> initializedHistoryPairs = new HashSet<>();

    private volatile boolean running;

    @VisibleForTesting
    FundingInfoTracker(
            Duration defaultFundingInterval,
            String fundingCoin,
            TradingPairSymbolRegistry tradingPairSymbolRegistry,
            EventSubscriber eventSubscriber
    ) {
        this.defaultFundingInterval = defaultFundingInterval;
        this.exchange = Exchange.BINANCE_DERIVATIVE;
        this.fundingCoin = fundingCoin;
        this.tradingPairSymbolRegistry = tradingPairSymbolRegistry;
        this.eventSubscriber = eventSubscriber;
        this.eventPublisher = EventPublisher.NOOP;
        this.applicationEventPublisher = event -> {};
        this.historyDataSource = (tradingPair, from, to) -> List.of(new FundingRatePoint(
                exchange,
                tradingPair,
                to,
                BigDecimal.ZERO,
                defaultFundingInterval
        ));
        this.taskScheduler = new SimpleAsyncTaskScheduler();
        this.historyProperties = new FundingHistoryProperties(
                Duration.ofDays(7),
                Duration.ofDays(1),
                List.of(Duration.ofSeconds(1))
        );
    }

    public FundingInfoTracker(
            Exchange exchange,
            Duration defaultFundingInterval,
            String fundingCoin,
            TradingPairSymbolRegistry tradingPairSymbolRegistry,
            EventSubscriber eventSubscriber,
            EventPublisher eventPublisher,
            ApplicationEventPublisher applicationEventPublisher,
            FundingRateHistoryDataSource historyDataSource,
            TaskScheduler taskScheduler,
            FundingHistoryProperties historyProperties
    ) {
        this.exchange = Objects.requireNonNull(exchange, "exchange");
        this.defaultFundingInterval = defaultFundingInterval;
        this.fundingCoin = fundingCoin;
        this.tradingPairSymbolRegistry = tradingPairSymbolRegistry;
        this.eventSubscriber = Objects.requireNonNull(eventSubscriber, "eventSubscriber");
        this.eventPublisher = Objects.requireNonNull(eventPublisher, "eventPublisher");
        this.applicationEventPublisher = Objects.requireNonNull(applicationEventPublisher, "applicationEventPublisher");
        this.historyDataSource = Objects.requireNonNull(historyDataSource, "historyDataSource");
        this.taskScheduler = Objects.requireNonNull(taskScheduler, "taskScheduler");
        this.historyProperties = Objects.requireNonNull(historyProperties, "historyProperties");
    }

    public FundingInfo getFundingInfo(String tradingPair) {
        FundingInfo fundingInfo = fundingInfos.get(tradingPair);
        if (fundingInfo == null || !fundingInfo.isInitialized()) return null;
        return fundingInfo;
    }

    public Set<String> getTradingPairs() {
        return new HashSet<>(tradingPairSymbolRegistry.getAllTradingPairs());
    }

    public boolean isHistoryReady(String tradingPair) {
        return initializedHistoryPairs.contains(tradingPair);
    }

    public List<FundingRatePoint> getFundingRateHistory(
            String tradingPair,
            Instant from,
            Instant to
    ) {
        NavigableMap<Instant, FundingRatePoint> history = confirmedHistory.get(tradingPair);
        if (history == null) return List.of();
        return List.copyOf(history.subMap(from, true, to, true).values());
    }

    public BigDecimal getAverageFundingRate(String tradingPair, Duration window) {
        List<FundingRatePoint> rates = recentHistory(tradingPair, window);
        return rates.stream()
                .map(FundingRatePoint::fundingRate)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(rates.size()), 18, RoundingMode.HALF_UP);
    }

    public BigDecimal getAverageDailyFundingRate(String tradingPair, Duration window) {
        List<FundingRatePoint> rates = recentHistory(tradingPair, window);
        return rates.stream()
                .map(FundingRatePoint::dailyRate)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(rates.size()), 18, RoundingMode.HALF_UP);
    }

    public BigDecimal getWeightedAverageDailyFundingRate(
            String tradingPair,
            Duration window,
            Duration halfLife
    ) {
        List<FundingRatePoint> rates = recentHistory(tradingPair, window);
        return movingAverage.calculate(rates, Instant.now(), halfLife);
    }

    @VisibleForTesting
    void processMessage(FundingInfoEvent.Received event) {
        String tradingPair = event.tradingPair();
        FundingInfo fundingInfo = fundingInfos.get(tradingPair);
        if (fundingInfo == null) {
            log.warn("등록되지 않은 tradingPair의 메시지는 처리할 수 없습니다");
            return;
        }
        fundingInfo.update(
                event.fundingInterval(),
                event.markPrice(),
                event.fundingRate(),
                event.nextFundingTime()
        );
        scheduleSettlement(tradingPair, event.nextFundingTime(), fundingInfo.getFundingInterval());
    }

    @VisibleForTesting
    void processIntervalMessage(FundingInfoEvent.IntervalReceived event) {
        event.snapshot().forEach((tradingPair, interval) -> {
            FundingInfo fundingInfo = fundingInfos.get(tradingPair);
            if (fundingInfo == null) {
                log.warn("등록되지 않은 tradingPair의 메시지는 처리할 수 없습니다");
                return;
            }
            fundingInfo.update(interval);
        });
    }

    @VisibleForTesting
    void registerTradingPairs(Set<String> tradingPairs) {
        tradingPairs.forEach(tradingPair ->
                fundingInfos.put(tradingPair, new FundingInfo(
                        tradingPair,
                        fundingCoin,
                        defaultFundingInterval
                ))
        );
    }

    @Override
    public void onStart() {
        running = true;
        registerTradingPairs(getTradingPairs());
        subscriptions.add(eventSubscriber.subscribe(
                FundingInfoEvent.Received.class,
                this::processMessage,
                ExecutionPolicy.sequential()
        ));
        subscriptions.add(eventSubscriber.subscribe(
                FundingInfoEvent.IntervalReceived.class,
                this::processIntervalMessage,
                ExecutionPolicy.sequential()
        ));
        subscriptions.add(eventSubscriber.subscribe(
                FundingInfoEvent.HistoryReceived.class,
                this::processHistory,
                ExecutionPolicy.sequential()
        ));
        subscriptions.add(eventSubscriber.subscribe(
                FundingInfoEvent.HistoryFetchFailed.class,
                this::processHistoryFailure,
                ExecutionPolicy.sequential()
        ));
        loadInitialHistory();
    }

    @Override
    public void onShutdown() {
        running = false;
        subscriptions.forEach(Subscription::close);
        subscriptions.clear();
        settlementTasks.values().forEach(task -> task.cancel(false));
        settlementTasks.clear();
    }

    @Override
    public int phase() {
        return Phases.FUNDING_INFO_TRACKER_SETUP;
    }

    private List<FundingRatePoint> recentHistory(String tradingPair, Duration window) {
        List<FundingRatePoint> rates = getFundingRateHistory(
                tradingPair,
                Instant.now().minus(window),
                Instant.now()
        );
        if (rates.isEmpty()) {
            throw new IllegalStateException("funding rate history is unavailable: " + tradingPair);
        }
        return rates;
    }

    private void loadInitialHistory() {
        Instant to = Instant.now();
        Instant from = to.minus(historyProperties.historyWindow());
        for (String tradingPair : getTradingPairs()) {
            List<FundingRatePoint> points = historyDataSource.fetch(tradingPair, from, to);
            if (points.isEmpty()) {
                throw new IllegalStateException("Initial funding history is empty: " + tradingPair);
            }
            applyConfirmed(points);
            initializedHistoryPairs.add(tradingPair);
        }
    }

    private void scheduleSettlement(
            String tradingPair,
            Instant fundingTime,
            Duration fundingInterval
    ) {
        if (fundingTime == null || !fundingTime.isAfter(Instant.now())) return;
        SettlementKey key = new SettlementKey(tradingPair, fundingTime);
        Duration firstDelay = historyProperties.settlementFetchDelays().getFirst();
        settlementTasks.computeIfAbsent(key, ignored -> taskScheduler.schedule(
                () -> requestSettlement(key, fundingInterval, 0),
                fundingTime.plus(firstDelay)
        ));
    }

    private void requestSettlement(SettlementKey key, Duration fundingInterval, int attempt) {
        if (!running) return;
        eventPublisher.publish(new FundingInfoEvent.HistoryFetchRequested(
                key.tradingPair(), key.fundingTime(), fundingInterval, attempt
        ));
    }

    @VisibleForTesting
    void processHistory(FundingInfoEvent.HistoryReceived event) {
        SettlementKey key = new SettlementKey(event.tradingPair(), event.fundingTime());
        if (!settlementTasks.containsKey(key)) return;
        applyConfirmed(event.points());
        if (containsConfirmed(key)) {
            settlementTasks.remove(key);
            eventPublisher.publish(new FundingInfoEvent.HistoryUpdated(event.tradingPair()));
            return;
        }
        scheduleSettlementRetry(key, event.fundingInterval(), event.attempt() + 1);
    }

    private void processHistoryFailure(FundingInfoEvent.HistoryFetchFailed event) {
        SettlementKey key = new SettlementKey(event.tradingPair(), event.fundingTime());
        if (!settlementTasks.containsKey(key)) return;
        settlementTasks.remove(key);
        log.warn("Confirmed funding rate fetch failed: source={}, pair={}, fundingTime={}, attempt={}",
                historyDataSource.getClass().getName(), event.tradingPair(), event.fundingTime(),
                event.attempt() + 1, event.cause());
        publishUnavailable(event.tradingPair(), event.cause());
    }

    private void scheduleSettlementRetry(
            SettlementKey key,
            Duration fundingInterval,
            int nextAttempt
    ) {
        List<Duration> delays = historyProperties.settlementFetchDelays();
        if (!running || nextAttempt >= delays.size()) {
            settlementTasks.remove(key);
            if (running) {
                publishUnavailable(
                        key.tradingPair(),
                        new IllegalStateException("Confirmed funding rate was not published by "
                                + delays.getLast() + " after " + key.fundingTime())
                );
            }
            return;
        }
        settlementTasks.put(key, taskScheduler.schedule(
                () -> requestSettlement(key, fundingInterval, nextAttempt),
                key.fundingTime().plus(delays.get(nextAttempt))
        ));
    }

    private void publishUnavailable(String tradingPair, Throwable cause) {
        applicationEventPublisher.publishEvent(new ExchangeUnavailableEvent(
                exchange,
                "FUNDING_HISTORY",
                tradingPair,
                cause
        ));
    }

    private boolean containsConfirmed(SettlementKey key) {
        NavigableMap<Instant, FundingRatePoint> history = confirmedHistory.get(key.tradingPair());
        if (history == null) return false;
        Instant lowerBound = key.fundingTime().minus(CONFIRMED_FUNDING_TIME_TOLERANCE);
        Instant upperBound = key.fundingTime().plus(CONFIRMED_FUNDING_TIME_TOLERANCE);
        return !history.subMap(lowerBound, true, upperBound, true).isEmpty();
    }

    private void applyConfirmed(List<FundingRatePoint> points) {
        if (points.isEmpty()) return;
        Map<String, NavigableMap<Instant, FundingRatePoint>> updated = new HashMap<>();
        confirmedHistory.forEach((pair, history) -> updated.put(pair, new TreeMap<>(history)));
        points.forEach(point -> updated
                .computeIfAbsent(point.tradingPair(), ignored -> new TreeMap<>())
                .put(point.fundingTime(), point));
        Instant cutoff = Instant.now().minus(historyProperties.historyWindow());
        updated.values().forEach(history -> history.headMap(cutoff, false).clear());
        Map<String, NavigableMap<Instant, FundingRatePoint>> snapshot = new HashMap<>();
        updated.forEach((pair, history) -> snapshot.put(
                pair,
                Collections.unmodifiableNavigableMap(new TreeMap<>(history))
        ));
        confirmedHistory = Map.copyOf(snapshot);
    }

    private void cancel(ScheduledFuture<?> task) {
        if (task != null) task.cancel(false);
    }

    private record SettlementKey(String tradingPair, Instant fundingTime) {}
}
