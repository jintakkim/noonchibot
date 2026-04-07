package com.hotak.noonchibot.core.balance;

import com.hotak.noonchibot.core.order.OrderCandidate;
import com.hotak.noonchibot.core.orderbook.OrderBookTracker;

import java.math.BigDecimal;


public class DerivativeBudgetChecker extends DefaultBudgetChecker {
    private final String collateralToken;

    public DerivativeBudgetChecker(
            AccountBalanceTracker balanceTracker,
            OrderBookTracker orderBookTracker,
            String collateralToken
            ) {
        super(balanceTracker, orderBookTracker);
        this.collateralToken = collateralToken;
    }

    @Override
    public BudgetCheckResult checkAndLock(OrderCandidate candidate) {
        if(candidate.getReduceOnly() != null && candidate.getReduceOnly()) {
            return new BudgetCheckResult(true, null, null);
        }
        return super.checkAndLock(candidate);
    }

    @Override
    protected BigDecimal estimateNotionalValue(OrderCandidate candidate) {
        return super.estimateNotionalValue(candidate);
    }

    /**
     * sell, buy 상관없이 quote 자산의 필요 크기를 리턴
     */
    @Override
    protected BigDecimal getRequiredCollateral(OrderCandidate candidate) {
        return estimateNotionalValue(candidate);
    }

    @Override
    protected String getCollateralToken(OrderCandidate candidate) {
        return collateralToken;
    }

}
