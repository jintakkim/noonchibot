package com.hotak.noonchibot.core.runtime;

import com.hotak.noonchibot.core.order.api.OrderCommandApi;
import com.hotak.noonchibot.core.order.api.OrderQueryApi;

/** 거래소 child context가 외부에 공개하는 진입점. */
public interface ExchangeApi {
    OrderCommandApi orderCommand();
    OrderQueryApi orderQuery();

}
