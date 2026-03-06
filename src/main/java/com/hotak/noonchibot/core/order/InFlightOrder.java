package com.hotak.noonchibot.core.order;

import com.hotak.noonchibot.core.datatype.TradeType;
import com.hotak.noonchibot.core.datatype.TradeUpdate;
import com.hotak.noonchibot.core.exception.InFlightUpdateFailedException;
import com.hotak.noonchibot.core.trade.fee.TokenAmount;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

@Slf4j
public class InFlightOrder {
    public enum State {
        PENDING_CREATE, OPEN, PENDING_CANCEL, CANCELED,
        PARTIALLY_FILLED, FILLED, FAILED, PENDING_APPROVAL,
        APPROVED, CREATED, COMPLETED;

        public boolean isAcceptedByExchange() {
            return this != PENDING_CREATE
                    && this != CANCELED
                    && this != FAILED
                    && this != PENDING_CANCEL;
        }

        public boolean isTerminal() {
            return this == CANCELED || this == FILLED || this == FAILED;
        }
    }

    private static final BigDecimal FILL_TOLERANCE = new BigDecimal("0.0000001");

    private final String clientOrderId;
    private final String tradingPair;
    private final OrderType orderType;
    private final TradeType tradeType;
    private final BigDecimal amount;
    private final BigDecimal price;
    private final Instant creationTimestamp;

    private String exchangeOrderId;
    private State currentState = State.PENDING_CREATE; // 초기값 설정
    private BigDecimal executedAmountBase = BigDecimal.ZERO;
    private BigDecimal executedAmountQuote = BigDecimal.ZERO;
    private Instant lastUpdateTimestamp;
    private final Map<String, TradeUpdate> orderFills = new HashMap<>();

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
    }

    public InFlightOrder(String clientOrderId, String tradingPair, OrderType orderType, TradeType tradeType,
                         BigDecimal amount, BigDecimal price, Instant creationTimestamp) {
        this(clientOrderId, tradingPair, orderType, tradeType, amount, price, creationTimestamp, null);
    }


    public synchronized LimitOrder toLimitOrder() {
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

    /**
     * @return 상태가 최종상태거나 수량이 다채워진 경우 true, 나머지 경우 false
     */
    public synchronized boolean isDone() {
        if (currentState.isTerminal()) {
            return true;
        }
        return executedAmountBase.compareTo(amount.abs()) >= 0;
    }

    public synchronized void updateWithTradeUpdate(TradeUpdate tradeUpdate) {
        String tradeId = tradeUpdate.tradeId();

        if (orderFills.containsKey(tradeId)) {
            log.warn("이미 처리된 tradeId: {} 입니다.", tradeId);
            return;
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
    }

    public synchronized void updateWithOrderUpdate(OrderUpdate orderUpdate) {
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

        boolean isChanged = !Objects.equals(prevExchangeOrderId, this.exchangeOrderId) || prevCurrentState != this.currentState;

        if (isChanged) {
            this.lastUpdateTimestamp = orderUpdate.updateTimestamp();
        }
    }

    /**
     * @return 채결 수량이 없다면 null을 리턴
     */
    public synchronized BigDecimal getAverageExecutedPrice() {
        if (executedAmountBase.compareTo(BigDecimal.ZERO) == 0 || orderFills.isEmpty()) {
            return null;
        }
        return executedAmountQuote.divide(executedAmountBase, MathContext.DECIMAL128);
    }

    public synchronized BigDecimal getCumulativeFeePaid() {
        return orderFills.values().stream()
                .flatMap(fill -> fill.fee().stream())
                .map(TokenAmount::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public synchronized boolean isOrderFilled() {
        BigDecimal remaining = amount.abs().subtract(executedAmountBase);
        return remaining.compareTo(FILL_TOLERANCE) <= 0;
    }

    public synchronized State getCurrentState() { return currentState; }
    public synchronized String getExchangeOrderId() { return exchangeOrderId; }
    public synchronized BigDecimal getExecutedAmountBase() { return executedAmountBase; }
    public synchronized BigDecimal getExecutedAmountQuote() { return executedAmountQuote; }
    public synchronized Instant getLastUpdateTimestamp() { return lastUpdateTimestamp; }
    public String getClientOrderId() { return clientOrderId; }
    public String getTradingPair() { return tradingPair; }
    public OrderType getOrderType() { return orderType; }
    public TradeType getTradeType() { return tradeType; }
    public BigDecimal getAmount() { return amount; }
    public BigDecimal getPrice() { return price; }
    public Instant getCreationTimestamp() { return creationTimestamp; }
    public String getBaseAsset() { return this.tradingPair.split("-")[0]; }
    public String getQuoteAsset() { return this.tradingPair.split("-")[1]; }
}