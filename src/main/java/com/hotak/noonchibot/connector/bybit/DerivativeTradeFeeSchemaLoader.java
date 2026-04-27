package com.hotak.noonchibot.connector.bybit;

import com.hotak.noonchibot.connector.AbstractTradeFeeSchemaLoader;
import com.hotak.noonchibot.connector.TradingPairSymbolRegistry;

import com.hotak.noonchibot.connector.web.RestAssistant;
import com.hotak.noonchibot.connector.web.RestRequest;
import com.hotak.noonchibot.core.IoExecutor;
import com.hotak.noonchibot.core.trade.fee.TradeFeeSchema;
import org.springframework.http.HttpMethod;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

class DerivativeTradeFeeSchemaLoader extends AbstractTradeFeeSchemaLoader {
    private final RestAssistant restAssistant;

    public DerivativeTradeFeeSchemaLoader(
            IoExecutor ioExecutor,
            TradingPairSymbolRegistry tradingPairSymbolRegistry,
            RestAssistant restAssistant
    ) {
        super(ioExecutor, tradingPairSymbolRegistry);
        this.restAssistant = restAssistant;
    }

    protected JsonNode fetchPairFeeSchema(String symbol) {
        return restAssistant.executeRequestAndGetJsonBody(
                RestRequest.builder()
                        .method(HttpMethod.GET)
                        .pathUrl(DerivativeApiSpec.COMMISSION_RATE_PATH_URL)
                        .params(Map.of("category", "linear", "symbol", symbol))
                        .authRequired(true)
                        .build()
        );
    }

    protected TradeFeeSchema parseSchema(JsonNode body) {
        JsonNode feeInfo = body.get("result").get("list").get(0);

        BigDecimal makerRate = new BigDecimal(feeInfo.get("makerFeeRate").asString());
        BigDecimal takerRate = new BigDecimal(feeInfo.get("takerFeeRate").asString());

        return new TradeFeeSchema(
                null,
                makerRate,
                takerRate,
                true, // 고정된 할인 로직 없음, 항상 최종이다
                List.of(),
                List.of()
        );
    }
}
