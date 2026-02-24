package com.hotak.noonchibot.connector.binance;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class BinanceApiSpec {
    public static final String REST_BASE_URL = "https://api.binance.com";
    public static final String PUBLIC_API_VERSION = "/v3";
    public static final String PRIVATE_API_VERSION = "/v3";
    public static final String TICKER_PRICE_CHANGE_PATH_URL = "/ticker/24hr";
    public static final String SNAPSHOT_PATH_URL = "/depth";


    public static int getTickerPriceChangeDynamicWeight(int symbolCount) {
        if (symbolCount == 0) return 80;          // symbols 생략
        if (symbolCount <= 20) return 2;          // 1-20
        if (symbolCount <= 100) return 40;        // 21-100
        return 80;                                 // 101+
    }
}
