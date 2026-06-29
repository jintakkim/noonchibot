package com.hotak.noonchibot.core.strategy.exposure;

import com.hotak.noonchibot.core.order.OrderCandidate;
import com.hotak.noonchibot.core.strategy.execution.ExecutionCommand;
import com.hotak.noonchibot.core.strategy.execution.ExecutionPlan;
import com.hotak.noonchibot.core.strategy.model.TargetOrderStyle;
import com.hotak.noonchibot.core.strategy.model.TargetPosition;
import com.hotak.noonchibot.core.trade.TradeType;

import java.math.BigDecimal;
import java.util.List;

public class ExposureReconciler {
    private final ReconcilePolicy policy;

    public ExposureReconciler(ReconcilePolicy policy) {
        this.policy = policy;
    }

    public ExecutionPlan reconcile(TargetPosition target, ExposureSnapshot exposure) {
        BigDecimal delta = target.targetBaseAmount().subtract(exposure.projectedBaseAmount());
        if (delta.abs().compareTo(policy.minDeltaBaseAmount()) <= 0) {
            return ExecutionPlan.empty();
        }

        return new ExecutionPlan(List.of(new ExecutionCommand.SubmitOrder(
                target.strategyId(),
                target.executionGroupId(),
                target.exchange(),
                target.reason(),
                target.urgency(),
                toOrderCandidate(target, delta)
        )));
    }

    private OrderCandidate toOrderCandidate(TargetPosition target, BigDecimal delta) {
        TradeType tradeType = delta.signum() > 0 ? TradeType.BUY : TradeType.SELL;
        TargetOrderStyle style = target.orderStyle();
        return OrderCandidate.builder()
                .tradingPair(target.tradingPair())
                .orderType(style.orderType())
                .tradeType(tradeType)
                .amount(delta.abs())
                .price(style.limitPrice())
                .timeInForce(style.timeInForce())
                .postOnly(style.postOnly())
                .reduceOnly(style.reduceOnly())
                .build();
    }
}
