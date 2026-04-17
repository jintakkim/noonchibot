package com.hotak.noonchibot.core.utils;

import lombok.NoArgsConstructor;

import java.util.concurrent.CompletionException;

@NoArgsConstructor
public final class AsyncUtils {
    public static Throwable unwrapCompletionException(Throwable e) {
        return e instanceof CompletionException && e.getCause() != null ? e.getCause() : e;
    }
}
