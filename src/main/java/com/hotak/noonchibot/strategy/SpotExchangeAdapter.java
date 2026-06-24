package com.hotak.noonchibot.strategy;

import com.hotak.noonchibot.core.balance.AccountBalanceTracker;
import com.hotak.noonchibot.core.orderbook.OrderBookTracker;
import com.hotak.noonchibot.core.trade.TradeFeeSchemaLoader;
import lombok.RequiredArgsConstructor;

import java.util.Map;

@RequiredArgsConstructor
public class SpotExchangeAdapter  {
    private final String platformName;
    private final AccountBalanceTracker accountBalanceTracker;
    private final TradeFeeSchemaLoader tradeFeeSchemaLoader;
    private final String stableCoinName;
    private final OrderBookTracker orderBookTracker;
    private final Map<String, StrategyPosition> strategyPositions;

//    @Override
//    public BigDecimal getAvailableBalance() {
//        return accountBalanceTracker.getAvailableBalance(stableCoinName);
//    }
//
//    @Override
//    public BigDecimal getCurrentTotalPositionUSD() {
//        return strategyPositions.values().stream().map(strategyPosition -> {
//            OrderBook orderBook = orderBookTracker.findOrderBook(strategyPosition.getTradingPair())
//                    .orElseThrow(() -> new IllegalStateException(strategyPosition.getTradingPair() + "에 대한 오더북이 초기화되지 않았습니다."));
//            VWAPForVolumeQueryResult result = orderBook.getVWAPForBaseVolume(false, strategyPosition.getSize());
//            return result.vwapPrice().multiply(result.fillableBaseVolume());
//        }).reduce(BigDecimal.ZERO, BigDecimal::add);
//    }
}
