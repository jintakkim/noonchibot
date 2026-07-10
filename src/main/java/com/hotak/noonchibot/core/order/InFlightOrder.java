package com.hotak.noonchibot.core.order;

import com.hotak.noonchibot.core.trade.TokenAmount;
import com.hotak.noonchibot.core.trade.TradeType;
import com.hotak.noonchibot.core.exception.InFlightUpdateFailedException;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.Instant;
import java.util.*;

@Slf4j
@Getter
public class InFlightOrder {
    private static final BigDecimal FILL_TOLERANCE = new BigDecimal("0.0000001");

    private final String clientOrderId;
    private final String tradingPair;
    private final OrderType orderType;
    private final TradeType tradeType;
    private final BigDecimal amount;
    private final BigDecimal price;
    private final Instant creationTimestamp;
    private final boolean postOnly;
    private final TimeInForce timeInForce;

    private final Set<String> processedTradeIds;
    private final Map<String, BigDecimal> accumulatedFees;
    private String exchangeOrderId;
    private OrderState currentState = OrderState.PENDING_CREATE; // 초기값 설정
    private BigDecimal executedAmountBase = BigDecimal.ZERO;
    private BigDecimal executedAmountQuote = BigDecimal.ZERO;
    private Instant lastUpdateTimestamp;

    public InFlightOrder(
            String clientOrderId,
            String tradingPair,
            OrderType orderType,
            TradeType tradeType,
            BigDecimal amount,
            BigDecimal price,
            Instant creationTimestamp,
            String exchangeOrderId,
            boolean postOnly,
            TimeInForce timeInForce,
            Set<String> processedTradeIds,
            Map<String, BigDecimal> accumulatedFees
    ) {
        this.clientOrderId = clientOrderId;
        this.tradingPair = tradingPair;
        this.orderType = orderType;
        this.tradeType = tradeType;
        this.amount = amount;
        this.price = price;
        this.postOnly = postOnly;
        this.timeInForce = timeInForce;
        this.creationTimestamp = creationTimestamp;
        this.lastUpdateTimestamp = creationTimestamp;
        this.exchangeOrderId = exchangeOrderId;
        this.processedTradeIds = processedTradeIds;
        this.accumulatedFees = accumulatedFees;
    }

    public InFlightOrder(
            String clientOrderId,
            String tradingPair,
            OrderType orderType,
            TradeType tradeType,
            BigDecimal amount,
            BigDecimal price,
            Instant creationTimestamp,
            boolean postOnly,
            TimeInForce timeInForce
    ) {
        this(
                clientOrderId,
                tradingPair,
                orderType,
                tradeType,
                amount, price,
                creationTimestamp,
                null,
                postOnly,
                timeInForce,
                new HashSet<>(),
                new HashMap<>()
        );
    }

    public static InFlightOrder restore(OrderView view) {
        InFlightOrder order = new InFlightOrder(
                view.clientOrderId(),
                view.tradingPair(),
                view.orderType(),
                view.tradeType(),
                view.amount(),
                view.price(),
                view.createdAt(),
                view.exchangeOrderId(),
                view.postOnly(),
                view.timeInForce(),
                new HashSet<>(view.processedTradeIds()),
                new HashMap<>(view.accumulatedFees())
        );
        order.currentState = view.state();
        order.executedAmountBase = view.executedBaseAmount();
        order.executedAmountQuote = view.executedQuoteAmount();
        order.lastUpdateTimestamp = view.updatedAt();
        return order;
    }

    /**
     * @return 상태가 최종상태거나 수량이 다채워진 경우 true, 나머지 경우 false
     */
    public boolean isDone() {
        if (currentState.isTerminal()) {
            return true;
        }
        return executedAmountBase.compareTo(amount.abs()) >= 0;
    }

    public boolean updateWithTradeUpdate(
            String tradeId,
            String clientOrderId,
            String exchangeOrderId,
            Instant fillTimestamp,
            BigDecimal fillPrice,
            BigDecimal fillBaseAmount,
            BigDecimal fillQuoteAmount,
            TokenAmount fee,
            Boolean isMaker
    ) {
        boolean clientIdMatches = clientOrderId != null && Objects.equals(clientOrderId, this.clientOrderId);
        boolean exchangeOrderIdMatches = exchangeOrderId != null && Objects.equals(exchangeOrderId, this.exchangeOrderId);
        if (!clientIdMatches && !exchangeOrderIdMatches) {
            throw new InFlightUpdateFailedException("주문 ID가 일치하지 않습니다.");
        }
        if (this.exchangeOrderId == null && exchangeOrderId != null) {
            this.exchangeOrderId = exchangeOrderId;
        }
        if(processedTradeIds.contains(tradeId)) {
            return false;
        }
        executedAmountBase = executedAmountBase.add(fillBaseAmount);
        executedAmountQuote = executedAmountQuote.add(fillQuoteAmount);
        if(fee != null)
            accumulatedFees.merge(
                    fee.token(),
                    fee.amount(),
                    BigDecimal::add
            );
        if (lastUpdateTimestamp == null || fillTimestamp.isAfter(lastUpdateTimestamp)) {
            lastUpdateTimestamp = fillTimestamp;
        }
        processedTradeIds.add(tradeId);
        return true;
    }


    public boolean updateWithOrderUpdate(
            String clientOrderId,
            String exchangeOrderId,
            OrderState newState,
            Instant updateTimestamp
    ) {
        if (updateTimestamp != null
                && lastUpdateTimestamp != null
                && updateTimestamp.isBefore(lastUpdateTimestamp)) {
            return false;
        }
        boolean clientOrderIdMatches = clientOrderId != null && Objects.equals(clientOrderId, this.clientOrderId);
        boolean exchangeOrderIdMatches = exchangeOrderId != null && Objects.equals(exchangeOrderId, this.exchangeOrderId);
        if (!clientOrderIdMatches && !exchangeOrderIdMatches) {
            throw new InFlightUpdateFailedException("주문 ID 불일치: client=" + clientOrderId + ", exchange=" + exchangeOrderId);
        }
        boolean canChangeState = newState != null
                && this.currentState != newState
                && this.currentState.canTransitionTo(newState);
        boolean canSetExchangeId = this.exchangeOrderId == null && exchangeOrderId != null;
        boolean canAdvanceTimestamp = updateTimestamp != null
                && (lastUpdateTimestamp == null || updateTimestamp.isAfter(lastUpdateTimestamp));

        if (!canChangeState && !canSetExchangeId && !canAdvanceTimestamp) {
            return false;
        }

        if (canSetExchangeId) {
            this.exchangeOrderId = exchangeOrderId;
        }
        if (canChangeState) {
            this.currentState = newState;
        }
        if (updateTimestamp != null) {
            this.lastUpdateTimestamp = updateTimestamp;
        }
        return true;
    }

    /**
     * @return 채결 수량이 없다면 null을 리턴
     */
    public BigDecimal getAverageExecutedPrice() {
        if (executedAmountBase.compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }
        return executedAmountQuote.divide(executedAmountBase, MathContext.DECIMAL128);
    }

    public boolean isOrderFilled() {
        BigDecimal remaining = amount.abs().subtract(executedAmountBase);
        return remaining.compareTo(FILL_TOLERANCE) <= 0;
    }

    public OrderView toView() {
        return new OrderView(
                clientOrderId,
                exchangeOrderId,
                tradingPair,
                orderType,
                tradeType,
                timeInForce,
                postOnly,
                currentState,
                amount,
                price,
                executedAmountBase,
                executedAmountQuote,
                amount.subtract(executedAmountBase),
                Set.copyOf(processedTradeIds),
                Map.copyOf(accumulatedFees),
                creationTimestamp,
                lastUpdateTimestamp
        );
    }
}
