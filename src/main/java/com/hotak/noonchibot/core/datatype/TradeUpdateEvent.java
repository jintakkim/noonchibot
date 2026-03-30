package com.hotak.noonchibot.core.datatype;

import com.hotak.noonchibot.core.event.ExchangeEvent;
import com.hotak.noonchibot.core.order.Trade;

import java.math.BigDecimal;
import java.time.Instant;

public record TradeUpdateEvent(
        String tradeId,
        String clientOrderId,
        String exchangeOrderId,
        String tradingPair,
        Instant fillTimestamp,
        BigDecimal fillPrice,
        BigDecimal fillBaseAmount,
        BigDecimal fillQuoteAmount,
        String feeToken,
        BigDecimal feeAmount,
        Boolean isMaker
) implements ExchangeEvent {

    public Trade toTrade() {
        return new Trade(
                tradeId,
                clientOrderId,
                exchangeOrderId,
                fillTimestamp,
                fillPrice,
                fillBaseAmount,
                fillQuoteAmount,
                feeToken,
                feeAmount,
                isMaker
        );
    }

}