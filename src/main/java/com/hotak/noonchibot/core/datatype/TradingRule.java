package com.hotak.noonchibot.core.datatype;

import java.math.BigDecimal;

public record TradingRule(
        String tradingPair,
        // 최소 주문 수량 ex) BTC/USDT: 0.0001 BTC
        BigDecimal minOrderSize,
        // 최대 주문 수량 ex) BTC/USDT: 9000 BTC
        BigDecimal maxOrderSize,
        // Base 수량 단위 ex) (lot size)0.00001 BTC
        BigDecimal minPriceIncrement,
        // Quote 수량 단위 ex) 0.01 USDT
        BigDecimal minBaseAmountIncrement,
        // 최소 주문 금액 (price × amount) ex) 10 USDT
        BigDecimal minNotionalSize,
        // 가격 유효 자릿수
        BigDecimal maxPriceSignificantDigits,
        boolean supportMarketOrder,
        boolean supportLimitOrder,
        // 매수 시 담보 토큰 ex) USDT
        String buyOrderCollateralToken,
        // 매도 시 담보 토큰 ex) BTC
        String sellOrderCollateralToken
) {
}
