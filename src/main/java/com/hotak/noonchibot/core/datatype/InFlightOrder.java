package com.hotak.noonchibot.core.datatype;

import com.hotak.noonchibot.core.order.OrderType;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@RequiredArgsConstructor
@Getter
public class InFlightOrder {
    public enum State {
        PENDING_CREATE,
        OPEN,
        PENDING_CANCEL,
        CANCELED,
        PARTIALLY_FILLED,
        FILLED,
        FAILED,
        PENDING_APPROVAL,
        APPROVED,
        CREATED,
        COMPLETED,
    }

    private final String clientOrderId;
    private final String tradingPair;
    private final OrderType orderType;
    private final TradeType tradeType;
    private final BigDecimal amount;
    private final BigDecimal price;
    private final Instant creationTimestamp;
    private final CompletableFuture<Void> completelyFilledEvent = new CompletableFuture<>();

    private String exchangeOrderId;
    private State  currentState;
    private BigDecimal executedAmountBase = BigDecimal.ZERO;
    private BigDecimal executedAmountQuote = BigDecimal.ZERO;
    private Instant lastUpdateTimestamp;

    private final Map<String, TradeUpdate> orderFills = new ConcurrentHashMap<>();

    /**
     * @return CANCEL, FILLED, FAILED returns true
     */
    public boolean isDone() {
        if (currentState == State.CANCELED || currentState == State.FILLED || currentState == State.FAILED) {
            return true;
        }
        return executedAmountBase.compareTo(amount) >= 0;
    }

    public String getBaseAsset() {
        return this.tradingPair.split("-")[0];
    }

    public String getQuoteAsset() {
        return this.tradingPair.split("-")[1];
    }

    /**
     * 체결 정보 업데이트 적용
     *
     * @param tradeUpdate 체결 정보
     * @return 업데이트 적용 여부
     */
    public boolean updateWithTradeUpdate(TradeUpdate tradeUpdate) {
        String tradeId = tradeUpdate.tradeId();

        // 이미 처리된 체결이거나 주문 ID 불일치
        boolean clientIdMatch = tradeUpdate.clientOrderId() != null
                && tradeUpdate.clientOrderId().equals(this.clientOrderId);
        boolean exchangeIdMatch = tradeUpdate.exchangeOrderId() != null
                && tradeUpdate.exchangeOrderId().equals(this.exchangeOrderId);

        if (orderFills.containsKey(tradeId) || (!clientIdMatch && !exchangeIdMatch)) {
            return false;
        }
        // 체결 정보 저장
        orderFills.put(tradeId, tradeUpdate);
        // 체결 수량 누적
        executedAmountBase = executedAmountBase.add(tradeUpdate.fillBaseAmount());
        executedAmountQuote = executedAmountQuote.add(tradeUpdate.fillQuoteAmount());
        this.lastUpdateTimestamp = tradeUpdate.fillTimestamp();
        checkFilledCondition();
        return true;
    }

    /**
     * 전량 체결 여부 확인
     */
    private void checkFilledCondition() {
        BigDecimal remaining = amount.abs().subtract(executedAmountBase);
        // 1e-8 이하면 전량 체결로 간주
        if (remaining.compareTo(new BigDecimal("0.00000001")) <= 0) {
            completelyFilledEvent.complete(null);
        }
    }
}
