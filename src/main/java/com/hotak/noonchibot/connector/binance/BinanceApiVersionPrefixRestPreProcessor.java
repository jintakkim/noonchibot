package com.hotak.noonchibot.connector.binance;

import com.hotak.noonchibot.connector.web.RestPreProcessor;
import com.hotak.noonchibot.connector.web.RestRequest;

public class BinanceApiVersionPrefixRestPreProcessor implements RestPreProcessor {
    @Override
    public RestRequest process(RestRequest request) {
        String version = request.authRequired()
                ? BinanceApiSpec.PRIVATE_API_VERSION
                : BinanceApiSpec.PUBLIC_API_VERSION;

        return request.toBuilder()
                .pathUrl(version + request.pathUrl())
                .build();
    }
}
