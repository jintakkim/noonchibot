package com.hotak.noonchibot.core.strategy.exposure;

import com.hotak.noonchibot.core.strategy.model.TargetOrderStyle;
import com.hotak.noonchibot.core.strategy.execution.ExecutionCommand;
import com.hotak.noonchibot.core.strategy.execution.ExecutionPlan;
import com.hotak.noonchibot.core.strategy.model.ExchangeOrderView;
import com.hotak.noonchibot.core.strategy.model.TargetPosition;

import com.hotak.noonchibot.core.order.OrderCandidate;
import com.hotak.noonchibot.core.trade.TradeType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class ExposureReconciler {
    private final ReconcilePolicy policy;

    public ExposureReconciler(ReconcilePolicy policy) {
        this.policy = policy;
    }

    public ExecutionPlan reconcile(
            TargetPosition target,
            ExposureSnapshot exposure,
            Collection<ExchangeOrderView> openOrders,
            Instant now
    ) {
        if (target.isExpired(now)) {
            return new ExecutionPlan(cancelOpenOrders(target, openOrders, "target expired"));
        }

        BigDecimal delta = target.targetBaseAmount().subtract(exposure.projectedBaseAmount());
        if (delta.abs().compareTo(policy.minDeltaBaseAmount()) <= 0) {
            return ExecutionPlan.empty();
        }

        List<ExecutionCommand> commands = new ArrayList<>();
        if (policy.cancelStaleOrdersBeforeSubmitting() && exposure.hasOpenOrders()) {
            commands.addAll(cancelOpenOrders(target, openOrders, "projected exposure differs from target"));
            return new ExecutionPlan(commands);
        }

        commands.add(new ExecutionCommand.SubmitOrder(
                target.strategyId(),
                target.exchange(),
                target.reason(),
                toOrderCandidate(target, delta)
        ));
        return new ExecutionPlan(commands);
    }

    private List<ExecutionCommand> cancelOpenOrders(
            TargetPosition target,
            Collection<ExchangeOrderView> openOrders,
            String reason
    ) {
        return openOrders.stream()
                .filter(order -> order.exchange() == target.exchange())
                .map(ExchangeOrderView::order)
                .filter(order -> order.tradingPair().equals(target.tradingPair()))
                .filter(order -> !order.state().isTerminal())
                .map(order -> new ExecutionCommand.CancelOrder(
                        target.strategyId(),
                        target.exchange(),
                        reason,
                        order.clientOrderId()
                ))
                .map(ExecutionCommand.class::cast)
                .toList();
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
