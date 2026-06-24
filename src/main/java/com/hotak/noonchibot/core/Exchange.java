package com.hotak.noonchibot.core;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Getter
public enum Exchange {
    BINANCE_SPOT("BINANCE_SPOT"),
    BINANCE_DERIVATIVE("BINANCE_DERIVATIVE"),
    HYPERLIQUID_DERIVATIVE("HYPERLIQUID_DERIVATIVE"),
    EDGEX_DERIVATIVE("EDGEX_DERIVATIVE"),
    BYBIT_SPOT("BYBIT_SPOT"),
    BYBIT_DERIVATIVE("BYBIT_DERIVATIVE"),
    OKX_SPOT("OKX_SPOT"),
    OKX_DERIVATIVE("OKX_DERIVATIVE"),
    LIGHTER_DERIVATIVE("LIGHTER_DERIVATIVE"),
    UPBIT_SPOT("UPBIT_SPOT"),
    ;

    private final String id;

    private static final Map<String, Exchange> BY_ID =
            Arrays.stream(values()).collect(Collectors.toUnmodifiableMap(Exchange::getId, e -> e));

    public static Exchange from(String id) {
        Exchange e = BY_ID.get(id);
        if (e == null) {
            throw new IllegalArgumentException("Unknown exchange id: " + id);
        }
        return e;
    }
}
