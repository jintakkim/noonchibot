package com.hotak.noonchibot.client.pricegap;

import com.hotak.noonchibot.connector.ExchangeConnector;
import com.hotak.noonchibot.connector.PriceCandleDataSource;
import com.hotak.noonchibot.connector.binance.BinanceConfig;
import com.hotak.noonchibot.connector.hyperliquid.HyperliquidConfig;
import com.hotak.noonchibot.core.Exchange;
import com.hotak.noonchibot.core.BootStrap;
import com.hotak.noonchibot.core.IoExecutor;
import com.hotak.noonchibot.core.RealtimeClock;
import com.hotak.noonchibot.core.price.StableQuotePriceConverter;
import com.hotak.noonchibot.core.pricegap.PriceGapFeedDefinition;
import com.hotak.noonchibot.core.pricegap.PriceGapSnapshotProducer;
import com.hotak.noonchibot.core.pricegap.PriceGapSnapshotStore;
import com.hotak.noonchibot.core.pricegap.PriceGapSnapshotPublisher;
import com.hotak.noonchibot.core.pricegap.PriceGapSubscriptionKey;
import com.hotak.noonchibot.core.pricegap.PriceGapSubscriptionRegistry;
import com.hotak.noonchibot.core.pricegap.history.PriceGapHistoryTracker;
import com.hotak.noonchibot.client.websocket.WebSocketSessions;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.TaskScheduler;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Configuration
public class PriceGapClientConfig {
    private static final String FEED_DEFINITIONS = "priceGapFeedDefinitions";

    @Bean
    public PriceGapSubscriptionRegistry priceGapSubscriptionRegistry() {
        return new PriceGapSubscriptionRegistry();
    }

    @Bean
    public PriceGapSnapshotStore priceGapSnapshotStore() {
        return new PriceGapSnapshotStore();
    }

    @Bean
    public PriceGapSnapshotPublisher priceGapSnapshotPublisher(
            PriceGapSubscriptionRegistry subscriptionRegistry,
            WebSocketSessions sessions
    ) {
        return new PriceGapWebSocketPublisher(subscriptionRegistry, sessions);
    }

    @Bean
    @Profile("!test")
    public PriceGapSnapshotProducer priceGapSnapshotProducer(
            PriceGapSubscriptionRegistry subscriptionRegistry,
            PriceGapSnapshotStore snapshotStore,
            PriceGapSnapshotPublisher snapshotPublisher,
            RealtimeClock realtimeClock,
            HyperliquidConfig.Properties hyperliquidProperties,
            List<ExchangeConnector> exchangeConnectors,
            @Qualifier(FEED_DEFINITIONS) List<PriceGapFeedDefinition> definitions
    ) {
        Map<Exchange, ExchangeConnector> connectors = exchangeConnectors.stream()
                .collect(Collectors.toUnmodifiableMap(
                        ExchangeConnector::getExchange,
                        Function.identity()
                ));
        PriceGapSnapshotProducer producer = new PriceGapSnapshotProducer(
                subscriptionRegistry,
                snapshotStore,
                snapshotPublisher,
                definitions,
                connectors.entrySet().stream().collect(Collectors.toUnmodifiableMap(
                        Map.Entry::getKey,
                        entry -> entry.getValue().getOrderBookTracker()
                )),
                hyperliquidProperties.network().isTestnet()
                        ? Map.of(Exchange.HYPERLIQUID_DERIVATIVE, Duration.ofMinutes(1))
                        : Map.of(),
                new StableQuotePriceConverter()
        );
        realtimeClock.addIterator(
                producer,
                requiredConnector(connectors, Exchange.BINANCE_DERIVATIVE).getSequentialDispatcher()
        );
        return producer;
    }

    @Bean(FEED_DEFINITIONS)
    @Profile("!test")
    public List<PriceGapFeedDefinition> priceGapFeedDefinitions(
            BinanceConfig.Properties binanceProperties,
            HyperliquidConfig.Properties hyperliquidProperties
    ) {
        return feedDefinitions(binanceProperties, hyperliquidProperties);
    }

    @Bean
    @Profile("!test")
    public PriceGapHistoryTracker priceGapHistoryTracker(
            @Qualifier(FEED_DEFINITIONS) List<PriceGapFeedDefinition> definitions,
            List<ExchangeConnector> exchangeConnectors,
            TaskScheduler taskScheduler,
            IoExecutor ioExecutor,
            BootStrap bootStrap
    ) {
        Map<Exchange, PriceCandleDataSource> dataSources = exchangeConnectors.stream()
                .filter(connector -> connector.getPriceCandleDataSource() != null)
                .collect(Collectors.toUnmodifiableMap(
                        ExchangeConnector::getExchange,
                        ExchangeConnector::getPriceCandleDataSource
                ));
        PriceGapHistoryTracker tracker = new PriceGapHistoryTracker(
                definitions,
                dataSources,
                taskScheduler,
                ioExecutor
        );
        bootStrap.register(tracker);
        return tracker;
    }

    private ExchangeConnector requiredConnector(
            Map<Exchange, ExchangeConnector> connectors,
            Exchange exchange
    ) {
        ExchangeConnector connector = connectors.get(exchange);
        if (connector == null) {
            throw new IllegalStateException("Exchange connector not found: " + exchange);
        }
        return connector;
    }

    private List<PriceGapFeedDefinition> feedDefinitions(
            BinanceConfig.Properties binance,
            HyperliquidConfig.Properties hyperliquid
    ) {
        Map<String, String> futuresPairs = binance.derivative().tradingPairSymbolMap();
        Map<String, String> hyperliquidPairs = hyperliquid.derivative().tradingPairSymbolMap();
        boolean hasStableRate = binance.spot().tradingPairSymbolMap().containsKey("USDC-USDT");
        if (!hasStableRate) return List.of();

        return futuresPairs.keySet().stream()
                .filter(pair -> pair.endsWith("-USDT"))
                .map(pair -> Map.entry(pair, pair.substring(0, pair.indexOf('-')) + "-USDC"))
                .filter(entry -> hyperliquidPairs.containsKey(entry.getValue()))
                .map(entry -> new PriceGapFeedDefinition(
                        new PriceGapSubscriptionKey(entry.getKey()),
                        Map.of(
                                Exchange.BINANCE_DERIVATIVE, entry.getKey(),
                                Exchange.HYPERLIQUID_DERIVATIVE, entry.getValue()
                        ),
                        Exchange.BINANCE_SPOT,
                        "USDC-USDT"
                ))
                .toList();
    }

}
