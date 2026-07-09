package com.hotak.noonchibot.connector;

import java.util.function.Predicate;

public interface ExchangeErrorClassifier extends Predicate<Throwable> {
    RuntimeException classify(ExchangeApiException exception);
}
