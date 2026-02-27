package com.hotak.noonchibot.connector.throttle;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

public interface AsyncThrottler {
     <T> CompletableFuture<T> execute(String limitId, Supplier<T> task,  Map<String, Integer> weightOverrides);
     <T> CompletableFuture<T> execute(String limitId, Supplier<T> task);
}
