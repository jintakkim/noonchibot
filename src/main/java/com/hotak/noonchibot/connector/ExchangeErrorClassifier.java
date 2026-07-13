package com.hotak.noonchibot.connector;

import com.hotak.noonchibot.connector.web.ExchangeRestApiException;

import java.util.function.Predicate;

public interface ExchangeErrorClassifier extends Predicate<Throwable> {
    RuntimeException classify(ExchangeRestApiException exception);
}
