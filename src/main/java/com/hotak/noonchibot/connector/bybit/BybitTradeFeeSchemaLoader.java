package com.hotak.noonchibot.connector.bybit;

import com.hotak.noonchibot.connector.TradingPairSymbolRegistry;
import com.hotak.noonchibot.connector.web.RestAssistant;
import com.hotak.noonchibot.connector.web.RestRequest;
import com.hotak.noonchibot.core.trade.fee.TradeFeeSchema;
import com.hotak.noonchibot.core.trade.fee.TradeFeeSchemaLoader;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpMethod;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@RequiredArgsConstructor
public class BybitTradeFeeSchemaLoader implements TradeFeeSchemaLoader {
    private final RestAssistant restAssistant;
    private final TradingPairSymbolRegistry symbolRegistry;

    private final Map<String, TradeFeeSchema> pairFeeSchemaCache = new HashMap<>();

    @Override
    public TradeFeeSchema get(String tradingPair) {
        return pairFeeSchemaCache.computeIfAbsent(tradingPair, this::fetchPairFeeSchema);
    }

    private TradeFeeSchema fetchPairFeeSchema(String tradingPair) {
        String symbol = symbolRegistry.convertTradingPairToExchangeSymbol(tradingPair);

        JsonNode body = restAssistant.executeRequestAndGetJsonBody(
            RestRequest.builder()
                    .method(HttpMethod.GET)
                    .pathUrl(BybitApiSpec.COMMISSION_RATE_PATH_URL)
                    .params(Map.of("category", "spot", "symbol", symbol))
                    .authRequired(true)
                    .build()
        );

        JsonNode feeInfo = body.get("result").get("list").get(0);

        BigDecimal makerRate = new BigDecimal(feeInfo.get("makerFeeRate").asString());
        BigDecimal takerRate = new BigDecimal(feeInfo.get("takerFeeRate").asString());

        // 바이빗은 기본적으로 baseCoin 또는 quoteCoin으로 수수료가 결정되므로 수수료 코인(feeToken)을 null로 처리하여 코어 룰을 따르게 함
        return new TradeFeeSchema(
                null, // 바이빗은 항상 거래하는 토큰을 수수료 토큰으로 이용한다
                makerRate,
                takerRate,
                true, // 고정된 할인 로직 없음
                List.of(),
                List.of()
        );
    }
}