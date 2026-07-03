package com.hotak.noonchibot.client.pricegap;

import com.hotak.noonchibot.connector.ExchangeConnector;
import com.hotak.noonchibot.connector.binance.BinanceConfig;
import com.hotak.noonchibot.connector.hyperliquid.HyperliquidConfig;
import com.hotak.noonchibot.core.Exchange;
import com.hotak.noonchibot.core.RealtimeClock;
import com.hotak.noonchibot.core.price.StableQuotePriceConverter;
import com.hotak.noonchibot.core.pricegap.PriceGapFeedDefinition;
import com.hotak.noonchibot.core.pricegap.PriceGapSnapshotProducer;
import com.hotak.noonchibot.core.pricegap.PriceGapSnapshotStore;
import com.hotak.noonchibot.core.pricegap.PriceGapSnapshotPublisher;
import com.hotak.noonchibot.core.pricegap.PriceGapSubscriptionKey;
import com.hotak.noonchibot.core.pricegap.PriceGapSubscriptionRegistry;
import com.hotak.noonchibot.client.websocket.WebSocketSessions;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Configuration
public class PriceGapClientConfig {
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
            BinanceConfig.Properties binanceProperties,
            HyperliquidConfig.Properties hyperliquidProperties,
            List<ExchangeConnector> exchangeConnectors
    ) {
        Map<Exchange, ExchangeConnector> connectors = exchangeConnectors.stream()
                .collect(Collectors.toUnmodifiableMap(
                        ExchangeConnector::getExchange,
                        Function.identity()
                ));
        List<PriceGapFeedDefinition> definitions = feedDefinitions(
                binanceProperties,
                hyperliquidProperties
        );
        PriceGapSnapshotProducer producer = new PriceGapSnapshotProducer(
                subscriptionRegistry,
                snapshotStore,
                snapshotPublisher,
                definitions,
                connectors.entrySet().stream().collect(Collectors.toUnmodifiableMap(
                        Map.Entry::getKey,
                        entry -> entry.getValue().getOrderBookTracker()
                )),
                new StableQuotePriceConverter()
        );
        realtimeClock.addIterator(
                producer,
                requiredConnector(connectors, Exchange.BINANCE_DERIVATIVE).getSequentialDispatcher()
        );
        return producer;
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
