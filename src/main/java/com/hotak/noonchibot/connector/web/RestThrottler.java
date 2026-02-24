package com.hotak.noonchibot.connector.web;

import java.util.function.Supplier;

public interface RestThrottler {
     <T> T execute(String limitId, Supplier<T> task, Integer customWeight);
     <T> T execute(String limitId, Supplier<T> task);
}
