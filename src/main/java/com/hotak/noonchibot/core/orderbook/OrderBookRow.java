package com.hotak.noonchibot.core.orderbook;

public record OrderBookRow(
        double price,
        double amount,
        long updateId
) {}
