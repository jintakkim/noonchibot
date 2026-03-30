package com.hotak.noonchibot.core.order;

import com.hotak.noonchibot.core.datatype.TradeType;
import com.hotak.noonchibot.core.datatype.TradeUpdateEvent;
import com.hotak.noonchibot.core.exception.InFlightUpdateFailedException;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.Instant;
import java.util.Objects;

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

    /**
     * 주문 트레킹에 실패했을 때 true
     */
    private boolean lost;
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
            String exchangeOrderId
    ) {
        this.clientOrderId = clientOrderId;
        this.tradingPair = tradingPair;
        this.orderType = orderType;
        this.tradeType = tradeType;
        this.amount = amount;
        this.price = price;
        this.creationTimestamp = creationTimestamp;
        this.lost = false;
        this.exchangeOrderId = exchangeOrderId;

    }

    public InFlightOrder(
            String clientOrderId,
            String tradingPair,
            OrderType orderType,
            TradeType tradeType,
            BigDecimal amount,
            BigDecimal price,
            Instant creationTimestamp
    ) {
        this(clientOrderId, tradingPair, orderType, tradeType, amount, price, creationTimestamp, null);
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

    public void updateWithTradeUpdate(TradeUpdateEvent tradeUpdateEvent) {
        boolean clientIdMatch = Objects.equals(tradeUpdateEvent.clientOrderId(), this.clientOrderId);
        boolean exchangeIdMatch = Objects.equals(tradeUpdateEvent.exchangeOrderId(), this.exchangeOrderId);

        if (!clientIdMatch && !exchangeIdMatch) {
            throw new InFlightUpdateFailedException("주문 ID가 일치하지 않습니다.");
        }
        executedAmountBase = executedAmountBase.add(tradeUpdateEvent.fillBaseAmount());
        executedAmountQuote = executedAmountQuote.add(tradeUpdateEvent.fillQuoteAmount());
        this.lastUpdateTimestamp = tradeUpdateEvent.fillTimestamp();
    }


    public void updateWithOrderUpdate(OrderUpdateEvent orderUpdateEvent) {
        if (!Objects.equals(orderUpdateEvent.clientOrderId(), this.clientOrderId) &&
                !Objects.equals(orderUpdateEvent.exchangeOrderId(), this.exchangeOrderId)) {
            throw new InFlightUpdateFailedException("주문 아이디가 일치하지 않습니다.");
        }

        String prevExchangeOrderId = this.exchangeOrderId;
        OrderState prevCurrentState = this.currentState;

        if (this.exchangeOrderId == null && orderUpdateEvent.exchangeOrderId() != null) {
            this.exchangeOrderId = orderUpdateEvent.exchangeOrderId();
        }

        this.currentState = orderUpdateEvent.newState();

        boolean isChanged = !Objects.equals(prevExchangeOrderId, this.exchangeOrderId) || prevCurrentState != this.currentState;

        if (isChanged) {
            this.lastUpdateTimestamp = orderUpdateEvent.updateTimestamp();
        }
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

    public String getBaseAsset() { return this.tradingPair.split("-")[0]; }
    public String getQuoteAsset() { return this.tradingPair.split("-")[1]; }
}