package com.hotak.noonchibot.core.event.internal.trade;

import com.hotak.noonchibot.core.event.internal.CoreEvent;
import com.hotak.noonchibot.core.trade.TokenAmount;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public sealed interface TradeEvent extends CoreEvent {
    record UpdateRequest(
            String clientOrderId,
            String exchangeOrderId,
            String tradingPair
    ) implements TradeEvent {}

    record Received(
            String clientOrderId,
            String exchangeOrderId,
            String tradingPair,
            List<Fill> fills
    ) implements TradeEvent {
    }

    record Fill(
            String tradeId,
            Instant fillTimestamp,
            BigDecimal fillPrice,
            BigDecimal fillBaseAmount,
            BigDecimal fillQuoteAmount,
            TokenAmount fee,
            Boolean isMaker
    ) {}
}
