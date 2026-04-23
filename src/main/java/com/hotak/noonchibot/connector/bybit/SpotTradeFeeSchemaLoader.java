package com.hotak.noonchibot.connector.bybit;

import com.hotak.noonchibot.connector.AbstractTradeFeeSchemaLoader;
import com.hotak.noonchibot.connector.TradingPairSymbolRegistry;
import com.hotak.noonchibot.connector.web.RestAssistant;
import com.hotak.noonchibot.connector.web.RestRequest;
import com.hotak.noonchibot.core.IoExecutor;
import com.hotak.noonchibot.core.MainExecutor;
import com.hotak.noonchibot.core.trade.fee.TradeFeeSchema;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpMethod;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Slf4j
class SpotTradeFeeSchemaLoader extends AbstractTradeFeeSchemaLoader {
    private final RestAssistant restAssistant;

    public SpotTradeFeeSchemaLoader(
            IoExecutor ioExecutor,
            MainExecutor mainExecutor,
            TradingPairSymbolRegistry tradingPairSymbolRegistry,
            RestAssistant restAssistant
    ) {
        super(ioExecutor, mainExecutor, tradingPairSymbolRegistry);
        this.restAssistant = restAssistant;
    }

    protected JsonNode fetchPairFeeSchema(String symbol) {
        return restAssistant.executeRequestAndGetJsonBody(
                RestRequest.builder()
                        .method(HttpMethod.GET)
                        .pathUrl(SpotApiSpec.COMMISSION_RATE_PATH_URL)
                        .params(Map.of("category", "spot", "symbol", symbol))
                        .authRequired(true)
                        .build()
        );
    }

    protected TradeFeeSchema parseSchema(JsonNode body) {
        JsonNode feeInfo = body.get("result").get("list").get(0);

        BigDecimal makerRate = new BigDecimal(feeInfo.get("makerFeeRate").asString());
        BigDecimal takerRate = new BigDecimal(feeInfo.get("takerFeeRate").asString());

        return new TradeFeeSchema(
                null, // Bybit 스팟은 방향/maker 여부에 따라 base 또는 quote가 수수료 토큰이 됨 (고정 토큰 없음)
                makerRate,
                takerRate,
                true, // 고정된 할인 로직 없음, 항상 최종이다
                List.of(),
                List.of()
        );
    }
}