package com.hotak.noonchibot.core.datatype;

import com.hotak.noonchibot.core.trade.fee.TradeFee;

import java.math.BigDecimal;
import java.time.Instant;

public record TradeUpdate(
        String tradeId,
        String clientOrderId,
        String exchangeOrderId,
        String tradingPair,
        Instant fillTimestamp,
        BigDecimal fillPrice,
        BigDecimal fillBaseAmount,
        BigDecimal fillQuoteAmount,
        TradeFee tradeFee,
        //nullable if dex
        Boolean isTaker
) {
}