package com.hotak.noonchibot.core.pricegap;

import com.hotak.noonchibot.core.Exchange;
import com.hotak.noonchibot.core.TimeIterator;
import com.hotak.noonchibot.core.orderbook.OrderBookTracker;
import com.hotak.noonchibot.core.price.LastTradePrice;
import com.hotak.noonchibot.core.price.NormalizedQuotePrice;
import com.hotak.noonchibot.core.price.StablePairPrice;
import com.hotak.noonchibot.core.price.StableQuotePriceConverter;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
public class PriceGapSnapshotProducer extends TimeIterator {
    private static final BigDecimal BPS_MULTIPLIER = new BigDecimal("10000");
    private static final Duration PUBLISH_INTERVAL = Duration.ofSeconds(1);
    private static final Duration MAXIMUM_PRICE_AGE = Duration.ofSeconds(5);
    private static final Duration MAXIMUM_STABLE_RATE_AGE = Duration.ofMinutes(1);

    private final PriceGapSubscriptionRegistry subscriptionRegistry;
    private final PriceGapSnapshotStore snapshotStore;
    private final PriceGapSnapshotPublisher snapshotPublisher;
    private final Map<PriceGapSubscriptionKey, PriceGapFeedDefinition> definitions;
    private final Map<Exchange, OrderBookTracker> orderBookTrackers;
    private final Map<Exchange, Duration> maximumPriceAges;
    private final StableQuotePriceConverter priceConverter;
    private Instant lastPublishedAt;
    private long sequence;
    private final Set<PriceGapSubscriptionKey> warnedMissingDefinitions = new HashSet<>();

    public PriceGapSnapshotProducer(
            PriceGapSubscriptionRegistry subscriptionRegistry,
            PriceGapSnapshotStore snapshotStore,
            PriceGapSnapshotPublisher snapshotPublisher,
            List<PriceGapFeedDefinition> definitions,
            Map<Exchange, OrderBookTracker> orderBookTrackers,
            Map<Exchange, Duration> maximumPriceAges,
            StableQuotePriceConverter priceConverter
    ) {
        this.subscriptionRegistry = Objects.requireNonNull(subscriptionRegistry, "subscriptionRegistry");
        this.snapshotStore = Objects.requireNonNull(snapshotStore, "snapshotStore");
        this.snapshotPublisher = snapshotPublisher == null ? PriceGapSnapshotPublisher.NOOP : snapshotPublisher;
        this.definitions = List.copyOf(definitions).stream()
                .collect(Collectors.toUnmodifiableMap(PriceGapFeedDefinition::key, Function.identity()));
        this.orderBookTrackers = Map.copyOf(orderBookTrackers);
        this.maximumPriceAges = Map.copyOf(maximumPriceAges);
        this.priceConverter = Objects.requireNonNull(priceConverter, "priceConverter");
    }

    @Override
    public void onTick(Instant timestamp) {
        super.onTick(timestamp);
        if (lastPublishedAt != null && timestamp.isBefore(lastPublishedAt.plus(PUBLISH_INTERVAL))) {
            return;
        }
        lastPublishedAt = timestamp;
        List<PriceGapSnapshot> snapshots = new ArrayList<>();
        for (PriceGapSubscriptionKey key : subscriptionRegistry.activeKeys()) {
            PriceGapFeedDefinition definition = definitions.get(key);
            if (definition == null) {
                if (warnedMissingDefinitions.add(key)) {
                    log.warn("Price gap request ignored because no feed is configured: {}", key.tradingPair());
                }
                continue;
            }
            createSnapshot(definition, timestamp).ifPresent(snapshot -> {
                snapshotStore.update(snapshot);
                snapshots.add(snapshot);
            });
        }
        if (!snapshots.isEmpty()) snapshotPublisher.publish(List.copyOf(snapshots));
    }

    private Optional<PriceGapSnapshot> createSnapshot(
            PriceGapFeedDefinition definition,
            Instant timestamp
    ) {
        String targetQuote = quoteAsset(definition.key().tradingPair());
        StablePairPrice stablePairPrice = stablePairPrice(definition, timestamp).orElse(null);
        List<NormalizedExchangePrice> prices = new ArrayList<>();

        definition.sourceTradingPairs().forEach((exchange, sourcePair) -> {
            Duration maximumPriceAge = maximumPriceAges.getOrDefault(exchange, MAXIMUM_PRICE_AGE);
            Optional<LastTradePrice> lastTrade = lastTradePrice(exchange, sourcePair);
            logLastTrade(definition.key(), "source", exchange, sourcePair,
                    lastTrade, timestamp, maximumPriceAge);
            lastTrade
                    .filter(trade -> !trade.timestamp().isBefore(timestamp.minus(maximumPriceAge)))
                    .flatMap(trade -> normalize(
                            exchange,
                            sourcePair,
                            trade,
                            targetQuote,
                            stablePairPrice
                    ))
                    .ifPresent(prices::add);
        });
        if (prices.isEmpty()) {
            return Optional.empty();
        }

        BigDecimal referencePrice = median(prices.stream()
                .map(price -> price.normalized().normalizedPrice())
                .toList());
        List<ExchangePriceGap> gaps = prices.stream()
                .sorted(Comparator.comparing(price -> price.exchange().name()))
                .map(price -> toGap(price, referencePrice))
                .toList();
        return Optional.of(new PriceGapSnapshot(
                definition.key(),
                timestamp,
                ++sequence,
                referencePrice,
                gaps,
                spread(gaps)
        ));
    }

    private Optional<StablePairPrice> stablePairPrice(
            PriceGapFeedDefinition definition,
            Instant timestamp
    ) {
        if (definition.stableRateExchange() == null) {
            return Optional.empty();
        }
        Optional<LastTradePrice> lastTrade = lastTradePrice(
                definition.stableRateExchange(),
                definition.stableRateTradingPair()
        );
        logLastTrade(definition.key(), "stable-rate", definition.stableRateExchange(),
                definition.stableRateTradingPair(), lastTrade, timestamp, MAXIMUM_STABLE_RATE_AGE);
        return lastTrade
                .filter(trade -> !trade.timestamp().isBefore(timestamp.minus(MAXIMUM_STABLE_RATE_AGE)))
                .map(trade -> StablePairPrice.fromTradingPair(
                        definition.stableRateTradingPair(),
                        trade.price(),
                        trade.timestamp()
                ));
    }

    private void logLastTrade(
            PriceGapSubscriptionKey key,
            String role,
            Exchange exchange,
            String tradingPair,
            Optional<LastTradePrice> lastTrade,
            Instant timestamp,
            Duration maximumAge
    ) {
        if (!log.isDebugEnabled()) return;
        if (lastTrade.isEmpty()) {
            log.debug("Price gap recent trade missing: key={}, role={}, exchange={}, pair={}",
                    key.tradingPair(), role, exchange, tradingPair);
            return;
        }
        LastTradePrice trade = lastTrade.get();
        long ageMillis = Duration.between(trade.timestamp(), timestamp).toMillis();
        boolean valid = !trade.timestamp().isBefore(timestamp.minus(maximumAge));
        log.debug("Price gap recent trade: key={}, role={}, exchange={}, pair={}, price={}, tradeTime={}, ageMs={}, valid={}",
                key.tradingPair(), role, exchange, tradingPair,
                trade.price(), trade.timestamp(), ageMillis, valid);
    }

    private Optional<LastTradePrice> lastTradePrice(Exchange exchange, String tradingPair) {
        OrderBookTracker tracker = orderBookTrackers.get(exchange);
        return tracker == null ? Optional.empty() : tracker.findLastTradePrice(tradingPair);
    }

    private Optional<NormalizedExchangePrice> normalize(
            Exchange exchange,
            String sourcePair,
            LastTradePrice lastTrade,
            String targetQuote,
            StablePairPrice stablePairPrice
    ) {
        try {
            return Optional.of(new NormalizedExchangePrice(
                    exchange,
                    lastTrade,
                    priceConverter.convert(sourcePair, lastTrade.price(), targetQuote, stablePairPrice)
            ));
        } catch (IllegalArgumentException | NullPointerException ignored) {
            return Optional.empty();
        }
    }

    private ExchangePriceGap toGap(NormalizedExchangePrice price, BigDecimal referencePrice) {
        NormalizedQuotePrice normalized = price.normalized();
        BigDecimal gapRate = normalized.normalizedPrice()
                .subtract(referencePrice)
                .divide(referencePrice, 12, RoundingMode.HALF_UP);
        BigDecimal gapBps = gapRate.multiply(BPS_MULTIPLIER);
        return new ExchangePriceGap(
                price.exchange(),
                normalized.originalTradingPair(),
                normalized.originalPrice(),
                normalized.normalizedPrice(),
                scaleGap(gapRate),
                scaleGap(gapBps),
                price.lastTrade().timestamp(),
                normalized.appliedQuoteRate(),
                normalized.rateTimestamp()
        );
    }

    private PriceGapSpread spread(List<ExchangePriceGap> gaps) {
        if (gaps.size() < 2) {
            return null;
        }
        ExchangePriceGap lowest = gaps.stream()
                .min(Comparator.comparing(ExchangePriceGap::normalizedPrice))
                .orElseThrow();
        ExchangePriceGap highest = gaps.stream()
                .max(Comparator.comparing(ExchangePriceGap::normalizedPrice))
                .orElseThrow();
        BigDecimal gapRate = highest.normalizedPrice()
                .subtract(lowest.normalizedPrice())
                .divide(lowest.normalizedPrice(), 12, RoundingMode.HALF_UP);
        BigDecimal gapBps = gapRate.multiply(BPS_MULTIPLIER);
        return new PriceGapSpread(
                lowest.exchange(),
                highest.exchange(),
                scaleGap(gapRate),
                scaleGap(gapBps)
        );
    }

    private BigDecimal scaleGap(BigDecimal value) {
        return value.setScale(6, RoundingMode.HALF_UP);
    }

    private BigDecimal median(List<BigDecimal> prices) {
        List<BigDecimal> sorted = prices.stream().sorted().toList();
        int middle = sorted.size() / 2;
        if (sorted.size() % 2 == 1) {
            return sorted.get(middle);
        }
        return sorted.get(middle - 1)
                .add(sorted.get(middle))
                .divide(BigDecimal.valueOf(2), 12, RoundingMode.HALF_UP);
    }

    private String quoteAsset(String tradingPair) {
        return tradingPair.substring(tradingPair.indexOf('-') + 1);
    }

    private record NormalizedExchangePrice(
            Exchange exchange,
            LastTradePrice lastTrade,
            NormalizedQuotePrice normalized
    ) {
    }
}
