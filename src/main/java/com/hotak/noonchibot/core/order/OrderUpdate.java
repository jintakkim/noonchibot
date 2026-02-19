package com.hotak.noonchibot.core.order;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.time.Instant;


@Getter
@RequiredArgsConstructor
public class OrderUpdate {
        private final String tradingPair;
        private final Instant updateTimestamp;
        private final OrderState newState;
        private final String clientOrderId;
        private final String exchangeOrderId;

        @Getter
        public static class FailedOrderUpdate extends OrderUpdate {
            private final String errorMessage;

            public FailedOrderUpdate(String tradingPair, Instant updateTimestamp, String clientOrderId, String exchangeOrderId, String errorMessage) {
                super(tradingPair, updateTimestamp, OrderState.FAILED, clientOrderId, exchangeOrderId);
                this.errorMessage = errorMessage;
            }
        }

}
