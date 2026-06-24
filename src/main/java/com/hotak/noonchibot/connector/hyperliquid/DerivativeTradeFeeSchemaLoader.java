package com.hotak.noonchibot.connector.hyperliquid;

import com.hotak.noonchibot.connector.AbstractTradeFeeSchemaLoader;
import com.hotak.noonchibot.connector.TradingPairSymbolRegistry;
import com.hotak.noonchibot.connector.web.RestAssistantImpl;
import com.hotak.noonchibot.connector.web.RestRequest;
import com.hotak.noonchibot.core.IoExecutor;
import com.hotak.noonchibot.core.trade.TradeFeeSchema;
import org.springframework.http.HttpMethod;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

class DerivativeTradeFeeSchemaLoader extends AbstractTradeFeeSchemaLoader {
    private final RestAssistantImpl restAssistant;
    private final String userAddress;

    public DerivativeTradeFeeSchemaLoader(
            IoExecutor ioExecutor,
            TradingPairSymbolRegistry tradingPairSymbolRegistry,
            RestAssistantImpl restAssistant,
            String userAddress
    ) {
        super(ioExecutor, tradingPairSymbolRegistry);
        this.restAssistant = restAssistant;
        this.userAddress = userAddress;
    }

    /**
     * Hyperliquid는 계정당 1번 호출로 모든 perp 자산 수수료를 알 수 있다.
     * userFees endpoint가 userAddRate(maker) / userCrossRate(taker)를 리턴.
     * 모든 perp 페어가 동일한 rate를 공유.
     *
     * todo: hlp-3, stable pair 는 별도 수수료 정책을 따른다. 해당 부분을 구현
     */
    @Override
    protected Map<String, TradeFeeSchema> loadPairFeeSchema(List<String> tradingPairs) {
        JsonNode response = restAssistant.executeRequestAndGetJsonBody(
                RestRequest.builder()
                        .method(HttpMethod.POST)
                        .pathUrl(DerivativeApiSpec.INFO_PATH_URL)
                        .body(Map.of(
                                "type", "userFees",
                                "user", userAddress
                        ))
                        .build()
        );

        TradeFeeSchema sharedSchema = parseSchema(response);
        return tradingPairs.stream().collect(Collectors.toMap(pair -> pair, pair -> sharedSchema));
    }

    @Override
    protected JsonNode fetchPairFeeSchema(String symbol) {
        throw new UnsupportedOperationException(
                "Hyperliquid uses bulk fetch via userFees endpoint - loadPairFeeSchema(List) is overridden");
    }

    @Override
    protected TradeFeeSchema parseSchema(JsonNode schema) {
        BigDecimal makerRate = schema.get("userAddRate").asDecimal();
        BigDecimal takerRate = schema.get("userCrossRate").asDecimal();
        return new TradeFeeSchema(
                null,
                makerRate,
                takerRate,
                false,
                List.of(),
                List.of()
        );
    }
}
