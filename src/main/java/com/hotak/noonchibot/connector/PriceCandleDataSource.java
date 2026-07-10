package com.hotak.noonchibot.connector;

import com.hotak.noonchibot.core.Exchange;
import com.hotak.noonchibot.core.pricegap.history.PriceCandle;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

public interface PriceCandleDataSource {
    Exchange exchange();

    List<PriceCandle> fetch(String tradingPair, Duration interval, Instant from, Instant to);
}
