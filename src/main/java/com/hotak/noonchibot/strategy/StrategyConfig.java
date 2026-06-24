package com.hotak.noonchibot.strategy;

import com.hotak.noonchibot.connector.DerivativeExchangeConnector;
import com.hotak.noonchibot.connector.ExchangeConnector;
import com.hotak.noonchibot.core.Exchange;
import com.hotak.noonchibot.strategy.arbitrage.ArbitrageOpportunityFinder;
import com.hotak.noonchibot.strategy.arbitrage.ArbitrageWatchlist;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Map;

@Configuration
public class StrategyConfig {
    @Bean
    public ArbitrageOpportunityFinder arbitrageOpportunityFinder(
            @Qualifier("binanceSpotExchangeConnector") ExchangeConnector binanceSpotExchangeConnector,
            @Qualifier("binanceDerivativeExchangeConnector") DerivativeExchangeConnector binanceDerivativeExchangeConnector,
            @Qualifier("hyperliquidDerivativeExchangeConnector") DerivativeExchangeConnector hyperliquidDerivativeExchangeConnector,
            @Qualifier("upbitSpotExchangeConnector") ExchangeConnector upbitSpotExchangeConnector,
            PriceNormalizer priceNormalizer
    ) {
        return new ArbitrageOpportunityFinder(
                new ArbitrageWatchlist(Map.of(
                        "BTC", List.of(
                                new ArbitrageWatchlist.Entity(Exchange.BINANCE_SPOT, "BTC-USDT"),
                                new ArbitrageWatchlist.Entity(Exchange.BINANCE_DERIVATIVE, "BTC-USDT"),
                                new ArbitrageWatchlist.Entity(Exchange.HYPERLIQUID_DERIVATIVE, "BTC-USDC"),
                                new ArbitrageWatchlist.Entity(Exchange.UPBIT_SPOT, "BTC-KRW")
                        )
                )),
                Map.of(
                        Exchange.BINANCE_SPOT, new ExchangeAdapterImpl<>(binanceSpotExchangeConnector),
                        Exchange.BINANCE_DERIVATIVE, new DerivativeExchangeAdapterImpl<>(binanceDerivativeExchangeConnector),
                        Exchange.HYPERLIQUID_DERIVATIVE, new DerivativeExchangeAdapterImpl<>(hyperliquidDerivativeExchangeConnector),
                        Exchange.UPBIT_SPOT, new ExchangeAdapterImpl<>(upbitSpotExchangeConnector)
                ),
                priceNormalizer
        );
    }

    @Bean
    public PriceNormalizer priceNormalizer(
            @Qualifier("binanceSpotExchangeConnector") ExchangeConnector binanceSpotExchangeConnector,
            @Qualifier("upbitSpotExchangeConnector") ExchangeConnector upbitSpotExchangeConnector
    ) {
        return new USDTBasedPriceNormalizer(
                new ExchangeAdapterImpl<>(binanceSpotExchangeConnector),
                new ExchangeAdapterImpl<>(upbitSpotExchangeConnector)
        );
    }

}
