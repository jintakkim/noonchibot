package com.hotak.noonchibot.core.orderbook;

public interface FundingInfoDataSource {
    FundingInfoMessage getFundingInfo(String tradingPair);
    FundingInfoMessageStream subscribe(String tradingPair);
    void unsubscribe(FundingInfoMessageStream stream);
}
