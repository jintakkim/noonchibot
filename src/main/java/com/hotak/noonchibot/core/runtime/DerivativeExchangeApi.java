package com.hotak.noonchibot.core.runtime;

import com.hotak.noonchibot.core.derivative.api.DerivativeModeCommandApi;

public interface DerivativeExchangeApi extends ExchangeApi {
    DerivativeModeCommandApi derivativeModeCommand();
}
