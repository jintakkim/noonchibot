package com.hotak.noonchibot.core.derivative.funding;

import com.hotak.noonchibot.core.Exchange;
import com.hotak.noonchibot.core.LifecycleAware;
import com.hotak.noonchibot.core.event.EventSubscriber;
import com.hotak.noonchibot.core.event.ExecutionPolicy;
import com.hotak.noonchibot.core.event.Subscription;
import com.hotak.noonchibot.core.event.internal.derivative.FundingInfoEvent;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableSet;
import java.util.TreeSet;

public class FundingGapHistoryService implements LifecycleAware {
    private final Map<Exchange, FundingInfoTracker> trackers;
    private final List<EventSubscriber> eventSubscribers;
    private final Map<String, FundingGapMarket> markets;
    private final Map<String, String> assetByTradingPair;
    private final TimeWeightedMovingAverage movingAverage;
    private final FundingHistoryProperties properties;
    private final List<Subscription> subscriptions = new ArrayList<>();
    private volatile FundingGapSnapshot snapshot = FundingGapSnapshot.EMPTY;

    public FundingGapHistoryService(
            Map<Exchange, FundingInfoTracker> trackers,
            List<EventSubscriber> eventSubscribers,
            List<FundingGapMarket> markets,
            TimeWeightedMovingAverage movingAverage,
            FundingHistoryProperties properties
    ) {
        this.trackers = Map.copyOf(trackers);
        this.eventSubscribers = List.copyOf(eventSubscribers);
        Map<String, FundingGapMarket> byAsset = new HashMap<>();
        Map<String, String> assetsByPair = new HashMap<>();
        markets.forEach(market -> {
            byAsset.put(market.asset(), market);
            market.tradingPairsByExchange().values().forEach(pair -> assetsByPair.put(pair, market.asset()));
        });
        this.markets = Map.copyOf(byAsset);
        this.assetByTradingPair = Map.copyOf(assetsByPair);
        this.movingAverage = movingAverage;
        this.properties = properties;
    }

    public FundingGapHistory history(String asset) {
        FundingGapHistory history = snapshot.histories().get(asset);
        if (history == null) throw new IllegalArgumentException("Funding gap is not configured: " + asset);
        return history;
    }

    public List<FundingGapSummary> summaries() {
        return snapshot.summaries();
    }

    @Override
    public void onStart() {
        refresh();
        eventSubscribers.forEach(eventSubscriber -> subscriptions.add(eventSubscriber.subscribe(
                FundingInfoEvent.HistoryUpdated.class,
                event -> refreshPair(event.tradingPair()),
                ExecutionPolicy.sequential()
        )));
    }

    @Override
    public void onShutdown() {
        subscriptions.forEach(Subscription::close);
        subscriptions.clear();
    }

    private void refresh() {
        Instant to = Instant.now();
        Instant from = to.minus(properties.historyWindow());
        Map<String, FundingGapHistory> next = new HashMap<>();
        markets.values().forEach(market -> next.put(
                market.asset(),
                createHistory(market, from, to, properties.weightedAverageHalfLife())
        ));
        snapshot = new FundingGapSnapshot(next, summaries(next));
    }

    private void refreshPair(String exchangeTradingPair) {
        String asset = assetByTradingPair.get(exchangeTradingPair);
        if (asset == null) return;
        FundingGapMarket market = markets.get(asset);
        Instant to = Instant.now();
        Map<String, FundingGapHistory> updated = new HashMap<>(snapshot.histories());
        updated.put(asset, createHistory(
                market,
                to.minus(properties.historyWindow()),
                to,
                properties.weightedAverageHalfLife()
        ));
        snapshot = new FundingGapSnapshot(updated, summaries(updated));
    }

    private List<FundingGapSummary> summaries(Map<String, FundingGapHistory> histories) {
        return histories.values().stream().map(this::summary).toList();
    }

    private FundingGapHistory createHistory(
            FundingGapMarket market,
            Instant from,
            Instant to,
            Duration halfLife
    ) {
        List<FundingExchangeHistory> exchanges = market.tradingPairsByExchange().entrySet().stream()
                .map(entry -> new FundingExchangeHistory(
                        entry.getKey(),
                        entry.getValue(),
                        weighted(points(entry.getKey(), entry.getValue(), from, to), halfLife)
                ))
                .filter(history -> !history.points().isEmpty())
                .sorted(Comparator.comparing(history -> history.exchange().name()))
                .toList();
        return new FundingGapHistory(
                market.asset(),
                from,
                to,
                halfLife,
                statistics(exchanges, to, halfLife),
                exchanges,
                gaps(exchanges)
        );
    }

    private List<FundingRatePoint> points(Exchange exchange, String pair, Instant from, Instant to) {
        FundingInfoTracker tracker = trackers.get(exchange);
        return tracker == null ? List.of() : tracker.getFundingRateHistory(pair, from, to);
    }

    private FundingGapStatistics statistics(
            List<FundingExchangeHistory> histories,
            Instant at,
            Duration halfLife
    ) {
        List<FundingExchangeStatistics> exchanges = histories.stream()
                .map(history -> {
                    return new FundingExchangeStatistics(
                            history.exchange(),
                            history.tradingPair(),
                            average(history.points()),
                            weightedAverage(history.points(), at, halfLife)
                    );
                })
                .toList();
        if (exchanges.size() < 2) {
            throw new IllegalStateException("Funding gap requires at least two exchange histories");
        }
        FundingExchangeStatistics low = exchanges.stream().min(Comparator.comparing(FundingExchangeStatistics::averageDailyRate)).orElseThrow();
        FundingExchangeStatistics high = exchanges.stream().max(Comparator.comparing(FundingExchangeStatistics::averageDailyRate)).orElseThrow();
        FundingExchangeStatistics weightedLow = exchanges.stream().min(Comparator.comparing(FundingExchangeStatistics::weightedAverageDailyRate)).orElseThrow();
        FundingExchangeStatistics weightedHigh = exchanges.stream().max(Comparator.comparing(FundingExchangeStatistics::weightedAverageDailyRate)).orElseThrow();
        return new FundingGapStatistics(
                exchanges,
                low.exchange(), high.exchange(), high.averageDailyRate().subtract(low.averageDailyRate()),
                weightedLow.exchange(), weightedHigh.exchange(),
                weightedHigh.weightedAverageDailyRate().subtract(weightedLow.weightedAverageDailyRate())
        );
    }

    private BigDecimal average(List<WeightedFundingRatePoint> points) {
        return points.stream().map(WeightedFundingRatePoint::dailyRate)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(points.size()), 18, RoundingMode.HALF_UP);
    }

    private BigDecimal weightedAverage(
            List<WeightedFundingRatePoint> points,
            Instant at,
            Duration halfLife
    ) {
        BigDecimal weightedSum = BigDecimal.ZERO;
        BigDecimal weightSum = BigDecimal.ZERO;
        for (WeightedFundingRatePoint point : points) {
            long ageMillis = Math.max(0, Duration.between(point.fundingTime(), at).toMillis());
            BigDecimal weight = BigDecimal.valueOf(Math.exp(
                    -Math.log(2.0) * ageMillis / halfLife.toMillis()
            ));
            weightedSum = weightedSum.add(point.dailyRate().multiply(weight, MathContext.DECIMAL128));
            weightSum = weightSum.add(weight);
        }
        return weightedSum.divide(weightSum, 18, RoundingMode.HALF_UP);
    }

    private List<WeightedFundingRatePoint> weighted(List<FundingRatePoint> points, Duration halfLife) {
        List<WeightedFundingRatePoint> result = new ArrayList<>(points.size());
        for (FundingRatePoint point : points) {
            result.add(new WeightedFundingRatePoint(
                    point.exchange(), point.tradingPair(), point.fundingTime(), point.fundingRate(),
                    point.dailyRate(), movingAverage.calculate(points, point.fundingTime(), halfLife)
            ));
        }
        return List.copyOf(result);
    }

    private List<FundingGapPoint> gaps(List<FundingExchangeHistory> histories) {
        NavigableSet<Instant> timeline = new TreeSet<>();
        histories.forEach(history -> history.points().forEach(point -> timeline.add(point.fundingTime())));
        Map<Exchange, Integer> indexes = new EnumMap<>(Exchange.class);
        Map<Exchange, WeightedFundingRatePoint> latest = new EnumMap<>(Exchange.class);
        List<FundingGapPoint> result = new ArrayList<>();
        for (Instant timestamp : timeline) {
            for (FundingExchangeHistory history : histories) {
                int index = indexes.getOrDefault(history.exchange(), 0);
                while (index < history.points().size()
                        && !history.points().get(index).fundingTime().isAfter(timestamp)) {
                    latest.put(history.exchange(), history.points().get(index++));
                }
                indexes.put(history.exchange(), index);
            }
            if (latest.size() < 2) continue;
            Map.Entry<Exchange, WeightedFundingRatePoint> low = latest.entrySet().stream().min(Comparator.comparing(entry -> entry.getValue().dailyRate())).orElseThrow();
            Map.Entry<Exchange, WeightedFundingRatePoint> high = latest.entrySet().stream().max(Comparator.comparing(entry -> entry.getValue().dailyRate())).orElseThrow();
            Map.Entry<Exchange, WeightedFundingRatePoint> weightedLow = latest.entrySet().stream().min(Comparator.comparing(entry -> entry.getValue().weightedAverageDailyRate())).orElseThrow();
            Map.Entry<Exchange, WeightedFundingRatePoint> weightedHigh = latest.entrySet().stream().max(Comparator.comparing(entry -> entry.getValue().weightedAverageDailyRate())).orElseThrow();
            result.add(new FundingGapPoint(
                    timestamp,
                    low.getKey(), high.getKey(), low.getValue().dailyRate(), high.getValue().dailyRate(),
                    high.getValue().dailyRate().subtract(low.getValue().dailyRate()),
                    weightedLow.getKey(), weightedHigh.getKey(),
                    weightedHigh.getValue().weightedAverageDailyRate().subtract(weightedLow.getValue().weightedAverageDailyRate())
            ));
        }
        return List.copyOf(result);
    }

    private FundingGapSummary summary(FundingGapHistory history) {
        FundingGapStatistics statistics = history.statistics();
        return new FundingGapSummary(
                history.asset(), statistics.lowestExchange(), statistics.highestExchange(),
                statistics.averageGap(), statistics.weightedLowestExchange(),
                statistics.weightedHighestExchange(), statistics.weightedAverageGap()
        );
    }
}
