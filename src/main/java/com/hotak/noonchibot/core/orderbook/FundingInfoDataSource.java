package com.hotak.noonchibot.core.orderbook;

import java.util.Set;

public interface FundingInfoDataSource {
    /**
     * blocking-method (rest fetch)
     */
    FundingInfoMessage getFundingInfo(String tradingPair);
    FundingInfoMessageStream subscribe(String tradingPair);

    /**
     * 여러개의 tradingPair를 구독한다면 해당 메서드를 사용
     * 단일 subscribe 메서드로 구독시 rateLimit이 발생할 수 있다.
     * 거래소에 따라 배치 지원이 안된다면 일반 subscribe을 여러번 호출한다.
     */
    FundingInfoMessageStream batchSubscribe(Set<String> tradingPairs);
    void unsubscribe(FundingInfoMessageStream stream);
}
