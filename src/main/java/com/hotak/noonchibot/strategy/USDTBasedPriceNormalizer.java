package com.hotak.noonchibot.strategy;

import com.hotak.noonchibot.core.orderbook.OrderBook;
import lombok.RequiredArgsConstructor;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.Map;

/**
 * 각 거래소의 usdt가격을 기준으로 가격을
 */
public class USDTBasedPriceNormalizer implements PriceNormalizer {
    private final Map<String, Handler> handlers;

    public USDTBasedPriceNormalizer(
            ExchangeAdapter binanceAdapter,
            ExchangeAdapter upbitAdapter
            ) {
        handlers = Map.of(
                "USDC", new BinanceUSDCUSDTBasedHandler(binanceAdapter),
                "KRW", new UpbitKRWUSDTBasedHandler(upbitAdapter)
        );
    }

    @Override
    public BigDecimal normalizeValue(String quoteAsset, BigDecimal value) {
        Handler handler = handlers.get(quoteAsset);
        if(handler == null) {
            throw new IllegalArgumentException("No handler registered for quote asset: " + quoteAsset);
        }
        return handler.handle(quoteAsset, value);
    }

    @Override
    public String getTargetAsset() {
        return "USDT";
    }

    private interface Handler {
        BigDecimal handle(String quoteAsset, BigDecimal value);
    }

    /**
     * upbit의 usdt를 기준으로 변환값을 계산한다.
     */
    @RequiredArgsConstructor
    private static class UpbitKRWUSDTBasedHandler implements Handler {
        private final ExchangeAdapter upbitAdapter;

        @Override
        public BigDecimal handle(String quoteAsset, BigDecimal value) {
            OrderBook orderBook = upbitAdapter.getOrderBookTracker().findOrderBook("USDT-KRW")
                    .orElseThrow(() -> new IllegalStateException("USDT-KRW orderbook not available - cannot normalizeValue KRW prices"));
            BigDecimal usdtKrwRate = orderBook.getLastTradePrice();
            return value.divide(usdtKrwRate, MathContext.DECIMAL64);
        }
    }

    /**
     * binance의 usdc를 기준으로 변환값을 계산한다.
     */
    @RequiredArgsConstructor
    private static class BinanceUSDCUSDTBasedHandler implements Handler {
        private final ExchangeAdapter binanceAdapter;

        @Override
        public BigDecimal handle(String quoteAsset, BigDecimal value) {
            OrderBook orderBook = binanceAdapter.getOrderBookTracker().findOrderBook("USDC-USDT")
                    .orElseThrow(() -> new IllegalStateException("USDC-USDT orderbook not available - cannot normalizeValue USDC prices"));
            BigDecimal usdcUsdtRate = orderBook.getLastTradePrice();
            return value.multiply(usdcUsdtRate, MathContext.DECIMAL64);
        }
    }
}
