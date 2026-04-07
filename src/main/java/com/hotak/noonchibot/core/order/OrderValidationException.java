package com.hotak.noonchibot.core.order;

public class OrderValidationException extends RuntimeException {
    public OrderValidationException(String message) {
        super(message);
    }

    public static class UnsupportedOrderTypeException extends OrderValidationException {
        public UnsupportedOrderTypeException(String message) {
            super(message);
        }
    }


    public static class UnsupportedTimeInForceException  extends OrderValidationException {
        public UnsupportedTimeInForceException(String message) {
            super(message);
        }
    }

    public static class BelowMinOrderSizeException  extends OrderValidationException {
        public BelowMinOrderSizeException (String message) {
            super(message);
        }
    }

    public static class BelowMinNotionalException  extends OrderValidationException {
        public BelowMinNotionalException (String message) {
            super(message);
        }
    }

}
