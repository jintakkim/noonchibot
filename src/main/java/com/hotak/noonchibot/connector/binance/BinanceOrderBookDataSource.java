package com.hotak.noonchibot.connector.binance;

import com.hotak.noonchibot.connector.TradingPairSymbolRegistry;
import com.hotak.noonchibot.connector.web.RestAssistant;
import com.hotak.noonchibot.core.orderbook.OrderBook;
import com.hotak.noonchibot.core.orderbook.OrderBookDataSource;
import com.hotak.noonchibot.core.orderbook.OrderBookMessage;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.BlockingQueue;

public class BinanceOrderBookDataSource implements OrderBookDataSource {
    private final static String TICKER_PRICE_CHANGE_PATH_URL = ""
    private final RestAssistant restAssistant;
    private final TradingPairSymbolRegistry tradingPairSymbolRegistry;


    @Override
    public OrderBook getNewOrderBook(String pair) {
        return new BinanceOrderBook();
    }

    @Override
    public void listenToOrderBookDiffs(BlockingQueue<OrderBookMessage.DiffMessage> queue) {

    }

    @Override
    public void listenToOrderBookSnapshots(BlockingQueue<OrderBookMessage.SnapshotMessage> queue) {

    }

    @Override
    public void listenToTrades(BlockingQueue<OrderBookMessage.TradeMessage> queue) {

    }

    @Override
    public boolean subscribeToTradingPair(String pair) {
        return false;
    }

    @Override
    public boolean unsubscribeFromTradingPair(String pair) {
        return false;
    }

    @Override
    public Map<String, BigDecimal> getLastTradedPrices(Set<String> tradingPairs) {
        return Map.of();
    }

    /**
     * using rest api
     */
    @Override
    public Map<String, BigDecimal> getLastTradedPrices(Set<String> tradingPairs, String domain) {
        return Map.of();
    }

    @Override
    public BigDecimal getLastTradedPrice(String tradingPair) {
        String exchangeSymbol = tradingPairSymbolRegistry.convertTradingPairToExchangeSymbol(tradingPair);

    }


}
