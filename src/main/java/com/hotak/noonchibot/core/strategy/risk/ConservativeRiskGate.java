package com.hotak.noonchibot.core.strategy.risk;

import com.hotak.noonchibot.core.strategy.snapshot.StrategySnapshot;
import com.hotak.noonchibot.core.strategy.execution.ExecutionCommand;
import com.hotak.noonchibot.core.strategy.execution.ExecutionPlan;
import com.hotak.noonchibot.core.strategy.api.StrategyContext;

import com.hotak.noonchibot.core.order.OrderCandidate;
import com.hotak.noonchibot.core.order.OrderType;
import com.hotak.noonchibot.core.trade.TradeType;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
public class ConservativeRiskGate implements RiskGate {
    private final RiskPolicy policy;

    public ConservativeRiskGate(RiskPolicy policy) {
        this.policy = policy == null ? RiskPolicy.conservative() : policy;
    }

    @Override
    public ExecutionPlan approve(ExecutionPlan plan, StrategyContext context) {
        List<ExecutionCommand> approved = new ArrayList<>();
        List<String> rejectionReasons = new ArrayList<>();

        for (ExecutionCommand command : plan.commands()) {
            Optional<String> rejectionReason = rejectionReason(command, context);
            if (rejectionReason.isPresent()) {
                rejectionReasons.add(rejectionReason.get());
                continue;
            }
            approved.add(command);
        }

        if (!rejectionReasons.isEmpty()) {
            publishRejectionSnapshot(context, rejectionReasons);
            if (policy.rejectWholePlanOnAnySubmitRejection()) {
                return new ExecutionPlan(plan.commands().stream()
                        .filter(ExecutionCommand.CancelOrder.class::isInstance)
                        .toList());
            }
        }

        return new ExecutionPlan(approved);
    }

    private Optional<String> rejectionReason(
            ExecutionCommand command,
            StrategyContext context
    ) {
        if (command instanceof ExecutionCommand.CancelOrder) {
            return Optional.empty();
        }
        ExecutionCommand.SubmitOrder submit = (ExecutionCommand.SubmitOrder) command;
        OrderCandidate candidate = submit.candidate();

        if (candidate.getAmount().signum() <= 0) {
            return Optional.of(submit.strategyId() + " rejected non-positive amount");
        }

        BigDecimal notional = estimatedNotional(candidate);
        if (notional != null) {
            BigDecimal maxNotional = policy.maxOrderNotionalByExchange().get(submit.exchange());
            if (maxNotional != null && notional.compareTo(maxNotional) > 0) {
                return Optional.of(submit.strategyId() + " rejected max notional. exchange="
                        + submit.exchange() + ", notional=" + notional + ", max=" + maxNotional);
            }
        }

        if (candidate.getTradeType() != TradeType.BUY || notional == null) {
            return Optional.empty();
        }

        String quoteAsset = quoteAsset(candidate.getTradingPair());
        Optional<BigDecimal> availableBalance = context.accountView().availableBalance(submit.exchange(), quoteAsset);
        if (availableBalance.isEmpty()) {
            if (policy.allowSubmitWhenBalanceUnknown()) {
                return Optional.empty();
            }
            return Optional.of(submit.strategyId() + " rejected unknown balance. exchange="
                    + submit.exchange() + ", asset=" + quoteAsset);
        }

        BigDecimal requiredBalance = notional.multiply(BigDecimal.ONE.add(policy.quoteBalanceBufferRate()));
        if (availableBalance.get().compareTo(requiredBalance) < 0) {
            return Optional.of(submit.strategyId() + " rejected insufficient balance. exchange="
                    + submit.exchange() + ", asset=" + quoteAsset
                    + ", available=" + availableBalance.get()
                    + ", required=" + requiredBalance);
        }

        return Optional.empty();
    }

    private BigDecimal estimatedNotional(OrderCandidate candidate) {
        if (candidate.getOrderType() == OrderType.MARKET && candidate.getPrice() == null) {
            return null;
        }
        if (candidate.getPrice() == null) {
            return null;
        }
        return candidate.getAmount().abs().multiply(candidate.getPrice());
    }

    private String quoteAsset(String tradingPair) {
        String[] parts = tradingPair.split("-");
        if (parts.length != 2) {
            throw new IllegalArgumentException("invalid tradingPair: " + tradingPair);
        }
        return parts[1];
    }

    private void publishRejectionSnapshot(
            StrategyContext context,
            List<String> rejectionReasons
    ) {
        log.warn("risk gate rejected execution plan: {}", rejectionReasons);
        context.snapshotSink().publish(new StrategySnapshot(
                "RISK_GATE",
                context.now(),
                "RISK_REJECTION",
                java.util.Map.of("reasons", List.copyOf(rejectionReasons))
        ));
    }
}
