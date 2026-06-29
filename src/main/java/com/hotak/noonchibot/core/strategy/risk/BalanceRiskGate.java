package com.hotak.noonchibot.core.strategy.risk;

import com.hotak.noonchibot.core.Exchange;
import com.hotak.noonchibot.core.order.OrderCandidate;
import com.hotak.noonchibot.core.strategy.api.StrategyContext;
import com.hotak.noonchibot.core.strategy.execution.ExecutionCommand;
import com.hotak.noonchibot.core.strategy.execution.ExecutionPlan;
import com.hotak.noonchibot.core.strategy.snapshot.StrategySnapshot;
import com.hotak.noonchibot.core.trade.TradeType;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
public class BalanceRiskGate implements RiskGate {
    private final BigDecimal balanceBufferRate;
    private final boolean allowUnknownBalance;

    public BalanceRiskGate() {
        this(BigDecimal.ZERO, false);
    }

    public BalanceRiskGate(BigDecimal balanceBufferRate, boolean allowUnknownBalance) {
        if (balanceBufferRate == null || balanceBufferRate.signum() < 0) {
            throw new IllegalArgumentException("balanceBufferRate must not be negative");
        }
        this.balanceBufferRate = balanceBufferRate;
        this.allowUnknownBalance = allowUnknownBalance;
    }

    @Override
    public ExecutionPlan approve(ExecutionPlan plan, StrategyContext context) {
        Map<BalanceKey, BigDecimal> requiredBalances = requiredBalances(plan, context);
        List<String> rejectionReasons = new ArrayList<>();

        requiredBalances.forEach((key, required) -> {
            Optional<BigDecimal> available = context.accountView().availableBalance(key.exchange(), key.asset());
            if (available.isEmpty()) {
                if (!allowUnknownBalance) {
                    rejectionReasons.add("unknown balance. exchange=" + key.exchange() + ", asset=" + key.asset());
                }
                return;
            }
            if (available.get().compareTo(required) < 0) {
                rejectionReasons.add("insufficient balance. exchange=" + key.exchange()
                        + ", asset=" + key.asset()
                        + ", available=" + available.get()
                        + ", required=" + required);
            }
        });

        if (rejectionReasons.isEmpty()) {
            return plan;
        }
        publishRejection(context, rejectionReasons);
        return new ExecutionPlan(plan.commands().stream()
                .filter(ExecutionCommand.CancelOrder.class::isInstance)
                .toList());
    }

    private Map<BalanceKey, BigDecimal> requiredBalances(
            ExecutionPlan plan,
            StrategyContext context
    ) {
        Map<BalanceKey, BigDecimal> requiredBalances = new HashMap<>();
        for (ExecutionCommand command : plan.commands()) {
            OrderRequest orderRequest = toOrderRequest(command);
            if (orderRequest == null || Boolean.TRUE.equals(orderRequest.candidate().getReduceOnly())) {
                continue;
            }
            BalanceRequirement requirement = requirement(orderRequest, context);
            requiredBalances.merge(requirement.key(), requirement.amount(), BigDecimal::add);
        }
        requiredBalances.replaceAll((key, amount) -> amount.multiply(BigDecimal.ONE.add(balanceBufferRate)));
        return requiredBalances;
    }

    private OrderRequest toOrderRequest(ExecutionCommand command) {
        if (command instanceof ExecutionCommand.SubmitOrder submit) {
            return new OrderRequest(submit.exchange(), submit.candidate());
        }
        if (command instanceof ExecutionCommand.ModifyOrder modify) {
            return new OrderRequest(modify.exchange(), modify.replacement());
        }
        return null;
    }

    private BalanceRequirement requirement(OrderRequest request, StrategyContext context) {
        OrderCandidate candidate = request.candidate();
        AssetPair assets = parseAssets(candidate.getTradingPair());
        if (isSpot(request.exchange()) && candidate.getTradeType() == TradeType.SELL) {
            return new BalanceRequirement(
                    new BalanceKey(request.exchange(), assets.baseAsset()),
                    candidate.getAmount().abs()
            );
        }

        BigDecimal price = candidate.getPrice();
        if (price == null) {
            price = context.marketView().executablePrice(
                    request.exchange(),
                    candidate.getTradingPair(),
                    candidate.getTradeType(),
                    candidate.getAmount().abs()
            );
        }
        return new BalanceRequirement(
                new BalanceKey(request.exchange(), assets.quoteAsset()),
                candidate.getAmount().abs().multiply(price)
        );
    }

    private boolean isSpot(Exchange exchange) {
        return exchange.name().endsWith("_SPOT");
    }

    private AssetPair parseAssets(String tradingPair) {
        String[] assets = tradingPair.split("[-_/]");
        if (assets.length != 2 || assets[0].isBlank() || assets[1].isBlank()) {
            throw new IllegalArgumentException("invalid tradingPair: " + tradingPair);
        }
        return new AssetPair(assets[0], assets[1]);
    }

    private void publishRejection(StrategyContext context, List<String> reasons) {
        log.warn("balance risk gate rejected execution plan: {}", reasons);
        context.snapshotSink().publish(new StrategySnapshot(
                "BALANCE_RISK_GATE",
                context.now(),
                "RISK_REJECTION",
                Map.of("reasons", List.copyOf(reasons))
        ));
    }

    private record OrderRequest(Exchange exchange, OrderCandidate candidate) {
    }

    private record BalanceRequirement(BalanceKey key, BigDecimal amount) {
    }

    private record BalanceKey(Exchange exchange, String asset) {
    }

    private record AssetPair(String baseAsset, String quoteAsset) {
    }
}
