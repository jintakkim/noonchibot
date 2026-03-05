package com.hotak.noonchibot.core.datatype;

import com.hotak.noonchibot.core.trade.fee.TokenAmount;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record TradeUpdate(
        String tradeId,
        String clientOrderId,
        String exchangeOrderId,
        String tradingPair,
        Instant fillTimestamp,
        BigDecimal fillPrice,
        BigDecimal fillBaseAmount,
        BigDecimal fillQuoteAmount,
        List<TokenAmount> fee,
        //nullable if dex
        Boolean isMaker
) {
}