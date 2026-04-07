package com.hotak.noonchibot.core.order;

import com.hotak.noonchibot.core.datatype.TradeType;
import com.hotak.noonchibot.core.datatype.TradeUpdateEvent;
import com.hotak.noonchibot.core.event.OrderUpdateEvent;
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
        if(processedTradeIds.contains(tradeUpdateEvent.tradeId())) {
            log.warn("이미 처리된 거래 건 입니다(tradeId: {})", tradeUpdateEvent.tradeId());
            return;
        }
        executedAmountBase = executedAmountBase.add(tradeUpdateEvent.fillBaseAmount());
        executedAmountQuote = executedAmountQuote.add(tradeUpdateEvent.fillQuoteAmount());
        if(tradeUpdateEvent.fee() != null)
            accumulatedFees.merge(
                    tradeUpdateEvent.fee().token(),
                    tradeUpdateEvent.fee().amount(),
                    BigDecimal::add
            );
        lastUpdateTimestamp = tradeUpdateEvent.fillTimestamp();
        processedTradeIds.add(tradeUpdateEvent.tradeId());
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
}