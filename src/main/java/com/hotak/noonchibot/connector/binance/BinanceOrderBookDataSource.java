package com.hotak.noonchibot.connector.binance;

import com.hotak.noonchibot.connector.TradingPairSymbolRegistry;
import com.hotak.noonchibot.connector.web.RestAssistant;
import com.hotak.noonchibot.connector.web.RestRequest;
import com.hotak.noonchibot.core.orderbook.OrderBook;
import com.hotak.noonchibot.core.orderbook.OrderBookDataSource;
import com.hotak.noonchibot.core.orderbook.OrderBookMessage;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.BlockingQueue;

public class BinanceOrderBookDataSource implements OrderBookDataSource {
    private final RestAssistant restAssistant;
    private final TradingPairSymbolRegistry tradingPairSymbolRegistry;


    @Override
    public OrderBook getNewOrderBook(String tradingPair) {
        return new BinanceOrderBook();
    }

    private OrderBookMessage.SnapshotMessage getOrderBookSnapshot(String tradingPair) {
        String exchangeSymbol = tradingPairSymbolRegistry.convertTradingPairToExchangeSymbol(tradingPair);
        BinanceOrderBook
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

    /**
     * using rest api
     */
    @Override
    public Map<String, BigDecimal> getLastTradedPrices(Set<String> tradingPairs) {
        if(tradingPairs == null || tradingPairs.isEmpty()) throw new IllegalArgumentException("한개 이상의 tradingPair가 전달되어야 합니다.");
        List<String> symbols = tradingPairs.stream()
                .map(tradingPairSymbolRegistry::convertTradingPairToExchangeSymbol)
                .toList();

        RestRequest request = RestRequest.builder()
                .pathUrl(BinanceApiSpec.TICKER_PRICE_CHANGE_PATH_URL)
                .params(Map.of("symbols", symbols))
                .customWeight(BinanceApiSpec.getTickerPriceChangeDynamicWeight(symbols.size()))
                .build();

        JsonNode response = restAssistant.executeRequestAndGetJsonBody(request);

        Map<String, BigDecimal> result = new HashMap<>();
        for (JsonNode ticker : response) {
            String exchangeSymbol = ticker.get("symbol").asString();
            String tradingPair = tradingPairSymbolRegistry.convertExchangeSymbolToTradingPair(exchangeSymbol);
            result.put(tradingPair, ticker.get("price").asDecimal());
        }
        return result;
    }

    /**
     * using rest api
     */
    @Override
    public BigDecimal getLastTradedPrice(String tradingPair) {
        String exchangeSymbol = tradingPairSymbolRegistry.convertTradingPairToExchangeSymbol(tradingPair);
        RestRequest request = RestRequest.builder()
                .pathUrl(BinanceApiSpec.TICKER_PRICE_CHANGE_PATH_URL)
                .params(Map.of("symbol", exchangeSymbol))
                .build();
        return restAssistant.executeRequestAndGetJsonBody(request).get("lastPrice").asDecimal();
    }
}
