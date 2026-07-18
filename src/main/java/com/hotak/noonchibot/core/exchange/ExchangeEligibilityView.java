package com.hotak.noonchibot.core.exchange;

import com.hotak.noonchibot.core.Exchange;

import java.util.Map;

public interface ExchangeEligibilityView {
    ExchangeEligibility eligibility(Exchange exchange);

    ExchangeEligibilityStatus status(Exchange exchange);

    boolean isEligible(Exchange exchange);

    Map<Exchange, ExchangeEligibility> snapshot();
}
