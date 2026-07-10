package com.hotak.noonchibot.client.funding;

import com.hotak.noonchibot.connector.DerivativeExchangeConnector;
import com.hotak.noonchibot.core.BootStrap;
import com.hotak.noonchibot.core.Exchange;
import com.hotak.noonchibot.core.derivative.funding.FundingGapMarket;
import com.hotak.noonchibot.core.derivative.funding.FundingGapHistoryService;
import com.hotak.noonchibot.core.derivative.funding.FundingHistoryProperties;
import com.hotak.noonchibot.core.derivative.funding.FundingInfoTracker;
import com.hotak.noonchibot.core.derivative.funding.TimeWeightedMovingAverage;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Configuration
@Profile("!test")
@EnableConfigurationProperties(FundingHistoryProperties.class)
public class FundingHistoryConfig {
    @Bean
    public FundingGapHistoryService fundingGapHistoryService(
            List<DerivativeExchangeConnector> connectors,
            FundingHistoryProperties properties,
            BootStrap bootStrap
    ) {
        Map<Exchange, FundingInfoTracker> trackers = connectors.stream()
                .collect(Collectors.toUnmodifiableMap(
                        DerivativeExchangeConnector::getExchange,
                        DerivativeExchangeConnector::getFundingInfoTracker
                ));
        FundingGapHistoryService service = new FundingGapHistoryService(
                trackers,
                connectors.stream()
                        .map(DerivativeExchangeConnector::getEventSubscriber)
                        .toList(),
                markets(connectors),
                new TimeWeightedMovingAverage(),
                properties
        );
        bootStrap.register(service);
        return service;
    }

    private List<FundingGapMarket> markets(List<DerivativeExchangeConnector> connectors) {
        Map<String, Map<Exchange, String>> pairsByBaseAsset = new HashMap<>();
        for (DerivativeExchangeConnector connector : connectors) {
            for (String tradingPair : connector.getFundingInfoTracker().getTradingPairs()) {
                String baseAsset = baseAsset(tradingPair);
                pairsByBaseAsset.computeIfAbsent(baseAsset, ignored -> new HashMap<>())
                        .merge(connector.getExchange(), tradingPair, this::preferUsdtPair);
            }
        }
        List<FundingGapMarket> markets = new ArrayList<>();
        pairsByBaseAsset.forEach((baseAsset, exchangePairs) -> {
            if (exchangePairs.size() < 2) return;
            markets.add(new FundingGapMarket(baseAsset, exchangePairs));
        });
        return markets.stream()
                .sorted(java.util.Comparator.comparing(FundingGapMarket::asset))
                .toList();
    }

    private String baseAsset(String tradingPair) {
        int separator = tradingPair.indexOf('-');
        return separator < 0 ? tradingPair : tradingPair.substring(0, separator);
    }

    private String preferUsdtPair(String current, String candidate) {
        return candidate.endsWith("-USDT") ? candidate : current;
    }
}
