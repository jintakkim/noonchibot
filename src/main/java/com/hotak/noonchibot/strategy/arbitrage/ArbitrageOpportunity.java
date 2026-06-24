package com.hotak.noonchibot.strategy.arbitrage;

import java.util.List;

public record ArbitrageOpportunity(
        String baseAsset,
        List<ExchangeQuote> quotes,        // 거래소별 가격/펀딩비
        List<GapPair> gaps
) {}