package com.hotak.noonchibot.connector;

import com.hotak.noonchibot.core.orderbook.AbstractOrderBook;
import com.hotak.noonchibot.core.orderbook.OrderBookQueryResult;
import com.hotak.noonchibot.core.orderbook.OrderBookRow;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public abstract class AbstractExchangeConnector {
    private final Map<String, String> symbolTradingPairMap = new HashMap<>();
    private final Map<String, String> tradingPairSymbolMap = new HashMap<>();


    @Override
    public List<String> getAllTradingPairs() {
        return new ArrayList<>(symbolTradingPairMap.values());
    }

    public String getExchangeSymbol(String tradingPair) {
        return tradingPairSymbolMap.get(tradingPair);
    }

    public String getTradingPair(String symbol) {
        return symbolTradingPairMap.get(symbol);
    }

    /**
     * 최우선 호가 리턴
     */
    public BigDecimal getPrice(String tradingPair, boolean isBuy) {
        AbstractOrderBook orderBook = getOrderBook(tradingPair);
        BigDecimal topPrice = orderBook.getPrice(isBuy);
        return quantizeOrderPrice(tradingPair, topPrice);
    }

    public abstract AbstractOrderBook getOrderBook(String tradingPair);

    public BigDecimal getMidPrice(String tradingPair) {
        return getPrice(tradingPair, true).add(getPrice(tradingPair, false)).divide(BigDecimal.TWO, RoundingMode.HALF_UP);
    }

    public abstract BigDecimal getLastTradedPrice(String tradingPair);

    /**
     * 특정 수량 채결시 평균 얼마에 사거나 팔 수 있는지 리턴
     */
    public OrderBookQueryResult getVwapForVolume(String tradingPair, boolean isBuy, BigDecimal volume) {
        AbstractOrderBook orderBook = getOrderBook(tradingPair);
        return orderBook.getVwapForVolume(isBuy, volume);
    }

    /**
     * Quote 기준 특정 수량 채결시 마지막 채결가 리턴
     */
    public OrderBookQueryResult getPriceForQuoteVolume(String tradingPair, boolean isBuy, BigDecimal volume) {
        AbstractOrderBook orderBook = getOrderBook(tradingPair);
        return orderBook.getPriceForQuoteVolume(isBuy, volume);
    }

    /**
     * Base 기준 특정 수량 채결시 마지막 채결가 리턴
     */
    public OrderBookQueryResult getPriceForVolume(String tradingPair, boolean isBuy, BigDecimal volume) {
        AbstractOrderBook orderBook = getOrderBook(tradingPair);
        return orderBook.getPriceForVolume(isBuy, volume);
    }

    /**
     * base수량 만큼 사기 위해 quote가 얼마나 필요한지 조회
     */
    public OrderBookQueryResult getQuoteVolumeForBaseVolume(String tradingPair, boolean isBuy, BigDecimal volume) {
        AbstractOrderBook orderBook = getOrderBook(tradingPair);
        return orderBook.getQuoteVolumeForBaseVolume(isBuy, volume);
    }

    /**
     * 해당 가격에서 살 수 있는 quote 양을 조회
     */
    public OrderBookQueryResult getQuoteVolumeForPrice(String tradingPair, boolean isBuy, BigDecimal price) {
        AbstractOrderBook orderBook = getOrderBook(tradingPair);
        return orderBook.getQuoteVolumeForPrice(isBuy, price);
    }

    /**
     * 해당 가격에서 살 수 있는 base 양을 조회
     */
    public OrderBookQueryResult getBaseVolumeForPrice(String tradingPair, boolean isBuy, BigDecimal price) {
        AbstractOrderBook orderBook = getOrderBook(tradingPair);
        return orderBook.getBaseVolumeForPrice(isBuy, price);
    }

    public List<OrderBookRow> getOrderBookBidEntries(String tradingPair) {
        AbstractOrderBook orderBook = getOrderBook(tradingPair);
        return orderBook.getBidEntries();
    }

    public List<OrderBookRow> getOrderBookAskEntries(String tradingPair) {
        AbstractOrderBook orderBook = getOrderBook(tradingPair);
        return orderBook.getAskEntries();
    }



}
