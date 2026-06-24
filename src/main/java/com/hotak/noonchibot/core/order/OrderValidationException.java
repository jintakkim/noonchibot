package com.hotak.noonchibot.core.order;

public class OrderValidationException extends RuntimeException {
    public final String clientOrderId;
    public final String tradingPair;

    public OrderValidationException(String clientOrderId, String tradingPair, String message) {
        super(message);
        this.clientOrderId = clientOrderId;
        this.tradingPair = tradingPair;
    }

    public static class UnsupportedOrderTypeException extends OrderValidationException {
        public UnsupportedOrderTypeException(String clientOrderId, String tradingPair, String message) {
            super(clientOrderId, tradingPair, message);
        }
    }


    public static class UnsupportedTimeInForceException  extends OrderValidationException {
        public UnsupportedTimeInForceException(String clientOrderId, String tradingPair, String message) {
            super(clientOrderId, tradingPair, message);
        }
    }

    public static class BelowMinOrderSizeException  extends OrderValidationException {
        public BelowMinOrderSizeException(String clientOrderId, String tradingPair, String message) {
            super(clientOrderId, tradingPair, message);
        }
    }

    public static class BelowMinNotionalException  extends OrderValidationException {
        public BelowMinNotionalException(String clientOrderId, String tradingPair, String message) {
            super(clientOrderId, tradingPair, message);
        }
    }

}
