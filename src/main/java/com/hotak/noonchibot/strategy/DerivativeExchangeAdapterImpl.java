package com.hotak.noonchibot.strategy;

import com.hotak.noonchibot.connector.DerivativeExchangeConnector;
import com.hotak.noonchibot.core.derivative.FundingInfoTracker;
import org.jspecify.annotations.NonNull;

public class DerivativeExchangeAdapterImpl<T extends DerivativeExchangeConnector> extends ExchangeAdapterImpl<T> implements DerivativeExchangeAdapter {
    public DerivativeExchangeAdapterImpl(T connector) {
        super(connector);
    }

    @Override
    public FundingInfoTracker getFundingInfoTracker() {
        return connector.getFundingInfoTracker();
    }

    @Override
    public boolean isSpot() {
        return false;
    }
}
