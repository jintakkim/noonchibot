package com.hotak.noonchibot.core.orderbook;

import java.util.Set;

public interface FundingInfoDataSource {
    void subscribe(Set<String> tradingPairs);
    void unsubscribe(Set<String> tradingPairs);
}
