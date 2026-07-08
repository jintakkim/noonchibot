package com.hotak.noonchibot.core.resilience;

import com.hotak.noonchibot.core.Exchange;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class CircuitBreakerNames {
    public static String rest(Exchange exchange) {
        return exchange.getId() + ".rest";
    }

    public static String orderEntry(Exchange exchange) {
        return exchange.getId() + ".order-entry";
    }

    public static String orderCancel(Exchange exchange) {
        return exchange.getId() + ".order-cancel";
    }

    public static String orderStatus(Exchange exchange) {
        return exchange.getId() + ".order-status";
    }

    public static String orderBook(Exchange exchange) {
        return exchange.getId() + ".order-book";
    }

    public static String balance(Exchange exchange) {
        return exchange.getId() + ".balance";
    }

    public static String trades(Exchange exchange) {
        return exchange.getId() + ".trades";
    }

    public static String tradingRules(Exchange exchange) {
        return exchange.getId() + ".trading-rules";
    }

    public static String tradeFee(Exchange exchange) {
        return exchange.getId() + ".trade-fee";
    }

    public static String funding(Exchange exchange) {
        return exchange.getId() + ".funding";
    }

    public static String userStream(Exchange exchange) {
        return exchange.getId() + ".user-stream";
    }

    public static String derivativeInfo(Exchange exchange) {
        return exchange.getId() + ".derivative-info";
    }

    public static String serverTime(Exchange exchange) {
        return exchange.getId() + ".server-time";
    }

    public static String transfer(Exchange exchange) {
        return exchange.getId() + ".transfer";
    }
}
