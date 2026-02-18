package com.hotak.noonchibot.connector.web;

import java.util.function.Supplier;

@FunctionalInterface
public interface Throttler {
     <T> T execute(String limitId, Supplier<T> task);
}
