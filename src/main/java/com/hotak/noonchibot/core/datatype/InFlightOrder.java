package com.hotak.noonchibot.core.datatype;

import lombok.RequiredArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@RequiredArgsConstructor
public class InFlightOrder {
    public enum State {
        PENDING_CREATE,
        OPEN,
        PENDING_CANCEL,
        CANCELED,
        PARTIALLY_FILLED,
        FILLED,
        FAILED,
        PENDING_APPROVAL,
        APPROVED,
        CREATED,
        COMPLETED,
    }

    public record TradeUpdate(
            String tradeId,
            String clientOrderId,
            String exchangeOrderId,
            String pair,
            Instant fillTimestamp,
            BigDecimal fillPrice,
            BigDecimal fillBaseAmount,
            BigDecimal fillQuoteAmount,
            TradeFee tradeFee
    ) {}

    private final String clientOrderId;
    private final String tradingPair;
    private final OrderType orderType;
    private final TradeType tradeType;
    private final BigDecimal amount;
    private final BigDecimal price;
    private final long creationTimestamp;

    private String exchangeOrderId;
    private State  currentState;
    private BigDecimal executedAmountBase = BigDecimal.ZERO;
    private BigDecimal executedAmountQuote = BigDecimal.ZERO;
    private long lastUpdateTimestamp;

    private final Map<String, TradeUpdate> orderFills = new ConcurrentHashMap<>();

    /**
     * @return CANCEL, FILLED, FAILED returns true
     */
    public boolean isDone() {
        if (currentState == State.CANCELED || currentState == State.FILLED || currentState == State.FAILED) {
            return true;
        }
        return executedAmountBase.compareTo(amount) >= 0;
    }

    public String getBaseAsset() {
        return this.tradingPair.split("-")[0];
    }

    public String getQuoteAsset() {
        return this.tradingPair.split("-")[1];
    }
}
