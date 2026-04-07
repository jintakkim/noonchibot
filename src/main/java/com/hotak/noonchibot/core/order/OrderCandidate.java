package com.hotak.noonchibot.core.order;

import com.hotak.noonchibot.core.datatype.TradeType;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.math.BigDecimal;

@Getter
@RequiredArgsConstructor
public class OrderCandidate {
    private final String tradingPair;
    private final OrderType orderType;
    private final TradeType tradeType;
    private final BigDecimal amount;
    private final BigDecimal price;
    private final boolean postOnly;
    private final TimeInForce timeInForce;

    // ---- derivative only fields ----
    private final Boolean reduceOnly;

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String tradingPair;
        private OrderType orderType;
        private TradeType tradeType;
        private BigDecimal amount;
        private BigDecimal price;
        private boolean postOnly = false;
        private TimeInForce timeInForce = TimeInForce.GTC;

        private Boolean reduceOnly = null;

        public Builder tradingPair(String tradingPair) {
            this.tradingPair = tradingPair;
            return this;
        }

        public Builder orderType(OrderType orderType) {
            this.orderType = orderType;
            return this;
        }

        public Builder tradeType(TradeType tradeType) {
            this.tradeType = tradeType;
            return this;
        }

        public Builder amount(BigDecimal amount) {
            this.amount = amount;
            return this;
        }

        public Builder price(BigDecimal price) {
            this.price = price;
            return this;
        }

        public Builder postOnly(boolean postOnly) {
            this.postOnly = postOnly;
            return this;
        }

        public Builder timeInForce(TimeInForce timeInForce) {
            this.timeInForce = timeInForce;
            return this;
        }

        public Builder reduceOnly(boolean reduceOnly) {
            this.reduceOnly = reduceOnly;
            return this;
        }

        public OrderCandidate build() {
            if(tradingPair == null || orderType == null || tradeType == null || amount == null || timeInForce == null) {
                throw new IllegalArgumentException("필수 필드는 null일 수 없습니다");
            }
            if(orderType == OrderType.LIMIT && price == null) {
                throw new IllegalArgumentException("limit 주문은 price를 지정해야합니다");
            }
            if(postOnly && timeInForce != TimeInForce.GTC) {
                throw new IllegalArgumentException("postOnly를 활성화하기 위해서는 TimeInForce 옵션이 GTC 이여야 합니다.");
            }
            return new OrderCandidate(
                    tradingPair,
                    orderType,
                    tradeType,
                    amount,
                    price,
                    postOnly,
                    timeInForce,
                    reduceOnly
            );
        }
    }
}
