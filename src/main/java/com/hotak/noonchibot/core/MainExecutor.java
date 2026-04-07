package com.hotak.noonchibot.core;

import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;

public interface MainExecutor extends Executor {
    Future<?> submit(Runnable task);
    <T> Future<T> submit(Callable<T> task);
}
