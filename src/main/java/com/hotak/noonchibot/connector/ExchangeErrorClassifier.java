package com.hotak.noonchibot.connector;

public interface ExchangeErrorClassifier {
    ExchangeErrorClassifier PASS_THROUGH = exception -> exception;

    RuntimeException classify(ExchangeApiException exception);
}
