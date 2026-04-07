package com.hotak.noonchibot.core.order;

import com.hotak.noonchibot.core.datatype.TradeType;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Getter
public class OrderHistory {
    @Id
    private String clientOrderId;
    private String platform;
    private String tradingPair;
    private OrderType orderType;
    private TradeType tradeType;
    private BigDecimal amount;
    private BigDecimal price;
    private String exchangeOrderId;
    private OrderState terminalState;
    private BigDecimal executedAmountBase;
    private BigDecimal executedAmountQuote;
    private BigDecimal avgExecutedPrice;
    private Instant creationTimestamp;
    private Instant lastUpdateTimestamp;

    @Convert(converter = JsonFeeConverter.class)
    @Column(columnDefinition = "json")
    private Map<String, BigDecimal> accumulatedFees;
    private boolean lost;

    public static OrderHistory from(InFlightOrder order, boolean lost, String platform) {
        return new OrderHistory(
                order.getClientOrderId(),
                platform,
                order.getTradingPair(),
                order.getOrderType(),
                order.getTradeType(),
                order.getAmount(),
                order.getPrice(),
                order.getExchangeOrderId(),
                order.getCurrentState(),
                order.getExecutedAmountBase(),
                order.getExecutedAmountQuote(),
                order.getAverageExecutedPrice(),
                order.getCreationTimestamp(),
                order.getLastUpdateTimestamp(),
                Map.copyOf(order.getAccumulatedFees()),
                lost
        );
    }
}
