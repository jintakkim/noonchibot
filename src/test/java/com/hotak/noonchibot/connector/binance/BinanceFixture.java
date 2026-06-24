package com.hotak.noonchibot.connector.binance;

import com.hotak.noonchibot.connector.SimpleTradingPairSymbolRegistry;
import com.hotak.noonchibot.connector.TradingPairSymbolRegistry;
import java.util.Map;

public final class BinanceFixture {
    private BinanceFixture() {}

    public static final TradingPairSymbolRegistry BTC_ETH_SOL_REGISTRY = new SimpleTradingPairSymbolRegistry(
            Map.of(
                    "BTC-USDT", "BTCUSDT",
                    "ETH-USDT", "ETHUSDT",
                    "SOL-USDT", "SOLUSDT"
            )
    );
}
