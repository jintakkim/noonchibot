package com.hotak.noonchibot.core.order;

import com.hotak.noonchibot.core.datatype.TradeType;
import com.hotak.noonchibot.core.datatype.TradeUpdate;
import com.hotak.noonchibot.core.exception.InFlightUpdateFailedException;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Getter
@Setter
public class InFlightOrder {
    public enum State {
        PENDING_CREATE, OPEN, PENDING_CANCEL, CANCELED,
        PARTIALLY_FILLED, FILLED, FAILED, PENDING_APPROVAL,
        APPROVED, CREATED, COMPLETED,
    }

    private final String clientOrderId;
    private final String tradingPair;
    private final OrderType orderType;
    private final TradeType tradeType;
    private final BigDecimal amount;
    private final BigDecimal price;
    private final Instant creationTimestamp;

    private final CompletableFuture<Void> completelyFilledEvent = new CompletableFuture<>();
    private final CompletableFuture<Void> processedByExchangeEvent = new CompletableFuture<>();

    private String exchangeOrderId;
    private State currentState = State.PENDING_CREATE; // 초기값 설정
    private BigDecimal executedAmountBase = BigDecimal.ZERO;
    private BigDecimal executedAmountQuote = BigDecimal.ZERO;
    private Instant lastUpdateTimestamp;

    private final Map<String, TradeUpdate> orderFills = new ConcurrentHashMap<>();

    public InFlightOrder(String clientOrderId, String tradingPair, OrderType orderType, TradeType tradeType,
                         BigDecimal amount, BigDecimal price, Instant creationTimestamp) {
        this(clientOrderId, tradingPair, orderType, tradeType, amount, price, creationTimestamp, null);
    }

    public InFlightOrder(String clientOrderId, String tradingPair, OrderType orderType, TradeType tradeType,
                         BigDecimal amount, BigDecimal price, Instant creationTimestamp, String exchangeOrderId) {
        this.clientOrderId = clientOrderId;
        this.tradingPair = tradingPair;
        this.orderType = orderType;
        this.tradeType = tradeType;
        this.amount = amount;
        this.price = price;
        this.creationTimestamp = creationTimestamp;
        this.exchangeOrderId = exchangeOrderId;

        if (exchangeOrderId != null) {
            this.processedByExchangeEvent.complete(null);
        }
    }

    public LimitOrder toLimitOrder() {
        return new LimitOrder(
                this.clientOrderId,
                this.tradingPair,
                this.orderType,
                this.getBaseAsset(),
                this.getQuoteAsset(),
                this.price,
                this.amount,
                this.executedAmountBase,
                this.creationTimestamp
        );
    }

    public boolean isDone() {
        if (currentState == State.CANCELED || currentState == State.FILLED || currentState == State.FAILED) {
            return true;
        }
        return executedAmountBase.compareTo(amount.abs()) >= 0;
    }

    /**
     * [수정] 반환값 보이드 + 예외 처리 버전
     */
    public void updateWithTradeUpdate(TradeUpdate tradeUpdate) {
        String tradeId = tradeUpdate.tradeId();

        if (orderFills.containsKey(tradeId)) {
            throw new InFlightUpdateFailedException("이미 처리된 Trade ID입니다: " + tradeId);
        }

        boolean clientIdMatch = Objects.equals(tradeUpdate.clientOrderId(), this.clientOrderId);
        boolean exchangeIdMatch = Objects.equals(tradeUpdate.exchangeOrderId(), this.exchangeOrderId);

        if (!clientIdMatch && !exchangeIdMatch) {
            throw new InFlightUpdateFailedException("주문 ID가 일치하지 않습니다.");
        }

        orderFills.put(tradeId, tradeUpdate);
        executedAmountBase = executedAmountBase.add(tradeUpdate.fillBaseAmount());
        executedAmountQuote = executedAmountQuote.add(tradeUpdate.fillQuoteAmount());
        this.lastUpdateTimestamp = tradeUpdate.fillTimestamp();

        checkFilledCondition();
    }

    public void updateWithOrderUpdate(OrderUpdate orderUpdate) {
        if (!Objects.equals(orderUpdate.clientOrderId(), this.clientOrderId) &&
                !Objects.equals(orderUpdate.exchangeOrderId(), this.exchangeOrderId)) {
            throw new InFlightUpdateFailedException("주문 아이디가 일치하지 않습니다.");
        }

        String prevExchangeOrderId = this.exchangeOrderId;
        State prevCurrentState = this.currentState;

        if (this.exchangeOrderId == null && orderUpdate.exchangeOrderId() != null) {
            this.exchangeOrderId = orderUpdate.exchangeOrderId();
        }

        this.currentState = orderUpdate.newState();

        if (this.currentState != State.PENDING_CREATE) {
            this.processedByExchangeEvent.complete(null);
        }

        boolean isChanged = !Objects.equals(prevExchangeOrderId, this.exchangeOrderId)
                || prevCurrentState != this.currentState;

        if (isChanged) {
            this.lastUpdateTimestamp = orderUpdate.updateTimestamp();
        }
    }

    public BigDecimal getAverageExecutedPrice() {
        if (executedAmountBase.compareTo(BigDecimal.ZERO) == 0 || orderFills.isEmpty()) {
            return BigDecimal.ZERO;
        }
        return executedAmountQuote.divide(executedAmountBase, MathContext.DECIMAL128);
    }

    public BigDecimal getCumulativeFeePaid() {
        return orderFills.values().stream()
                .map(fill -> {
                    if (fill.tradeFee() == null) return BigDecimal.ZERO;
                    return fill.tradeFee().getTotalAmount(fill.fillQuoteAmount());
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private void checkFilledCondition() {
        BigDecimal remaining = amount.abs().subtract(executedAmountBase);
        if (remaining.compareTo(new BigDecimal("0.00000001")) <= 0) {
            completelyFilledEvent.complete(null);
        }
    }

    public String getBaseAsset() { return this.tradingPair.split("-")[0]; }

    public String getQuoteAsset() { return this.tradingPair.split("-")[1]; }
}