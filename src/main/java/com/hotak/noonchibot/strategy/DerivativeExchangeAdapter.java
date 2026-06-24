package com.hotak.noonchibot.strategy;

import com.hotak.noonchibot.core.derivative.FundingInfoTracker;

public interface DerivativeExchangeAdapter extends ExchangeAdapter {
    FundingInfoTracker getFundingInfoTracker();
}
