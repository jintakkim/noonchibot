package com.hotak.noonchibot.core.balance;

import com.hotak.noonchibot.core.order.OrderCandidate;
import com.hotak.noonchibot.core.order.OrderType;
import com.hotak.noonchibot.core.orderbook.OrderBook;
import com.hotak.noonchibot.core.orderbook.OrderBookQueryResult;
import com.hotak.noonchibot.core.orderbook.OrderBookTracker;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/**
 * - SPOT 전용
 * strategy에서 사용할 수 있는 담보가 충분한지 체크하는 체커이다.
 * balanceTracker와 거래소간의 딜레이가 있기 떄문에 해당 객체를 사용하더라도 주문시 not enough margin이 발생할 수 있다.
 * OrderType이 Market인 경우에는 오더북에서
 *
 * tradeType이 sell인 경우에는 BaseAsset의 required, available이 리턴된다.
 * tradeType이 buy인 경우에는 QuoteAsset의 required, available이 리턴된다.
 */
public class DefaultBudgetChecker {
    protected final AccountBalanceTracker balanceTracker;
    private final OrderBookTracker orderBookTracker;
    private final Map<String, BigDecimal> lockedCollateral = new HashMap<>();

    public DefaultBudgetChecker(AccountBalanceTracker balanceTracker, OrderBookTracker orderBookTracker) {
        this.balanceTracker = balanceTracker;
        this.orderBookTracker = orderBookTracker;
    }

    public BudgetCheckResult checkAndLock(OrderCandidate candidate) {
        String collateral = getCollateralToken(candidate);
        BigDecimal required = getRequiredCollateral(candidate);
        BigDecimal available = getAvailableBalance(collateral);

        if (available.compareTo(required) < 0) {
            return new BudgetCheckResult(false, required, available);
        }
        lockedCollateral.merge(collateral, required, BigDecimal::add);
        return new BudgetCheckResult(true, required, available);
    }

    protected BigDecimal getRequiredCollateral(OrderCandidate candidate) {
        return switch (candidate.getTradeType()) {
            case BUY -> estimateNotionalValue(candidate);   // 매수: quote 자산 필요 (ex: USDT)
            case SELL -> candidate.getAmount();                // 매도: base 자산 필요 (ex: BTC)
        };
    }

    protected BigDecimal getAvailableBalance(String collateral) {
        BigDecimal available = balanceTracker.getAvailableBalance(collateral);
        BigDecimal locked = lockedCollateral.getOrDefault(collateral, BigDecimal.ZERO);
        return available.subtract(locked);
    }

    protected BigDecimal estimateNotionalValue(OrderCandidate candidate) {
        if (candidate.getOrderType() == OrderType.LIMIT) {
            return candidate.getAmount().multiply(candidate.getPrice());
        }
        // 시장가: 오더북에서 impact price 추정
        OrderBook orderBook = orderBookTracker
                .findOrderBook(candidate.getTradingPair())
                .orElseThrow(() -> new IllegalStateException("주문 금액을 측정할 수 없습니다(오더북이 존재하지 않음)"));

        OrderBookQueryResult queryResult = switch (candidate.getTradeType()) {
            case BUY -> orderBook.getImpactPriceForBaseVolume(true, candidate.getAmount());
            case SELL -> orderBook.getImpactPriceForBaseVolume(false, candidate.getAmount());
        };
        return candidate.getAmount().multiply(queryResult.resultPrice());
    }

    protected String getCollateralToken(OrderCandidate candidate) {
        String[] parts = candidate.getTradingPair().split("-");
        return switch (candidate.getTradeType()) {
            case BUY -> parts[1];   // quote: USDT
            case SELL -> parts[0];  // base: BTC
        };
    }
}