package com.hotak.noonchibot.core.datatype;

import com.hotak.noonchibot.core.event.ExchangeEvent;
import com.hotak.noonchibot.core.order.Trade;
import com.hotak.noonchibot.core.trade.fee.TokenAmount;

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
        TokenAmount fee,
        Boolean isMaker
) implements ExchangeEvent {

    public Trade toTrade(String platform) {
        return new Trade(
                tradeId,
                platform,
                clientOrderId,
                exchangeOrderId,
                fillTimestamp,
                fillPrice,
                fillBaseAmount,
                fillQuoteAmount,
                fee == null ? null : fee.token(),
                fee == null ? null : fee.amount(),
                isMaker
        );
    }

}