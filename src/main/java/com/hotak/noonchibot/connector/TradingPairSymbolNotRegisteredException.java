package com.hotak.noonchibot.connector;

public class TradingPairSymbolNotRegisteredException extends IllegalArgumentException {
    public TradingPairSymbolNotRegisteredException(String message) {
        super(message);
    }
}
