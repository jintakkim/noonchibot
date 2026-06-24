package com.hotak.noonchibot.connector.transfer;

public class FundTransferException extends RuntimeException {
    public FundTransferException(String message, Exception e) {
        super(message, e);
    }

    public FundTransferException(String message) {
        super(message);
    }
}
