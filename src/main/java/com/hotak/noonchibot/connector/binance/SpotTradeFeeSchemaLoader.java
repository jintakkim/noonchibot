package com.hotak.noonchibot.connector.binance;

import com.hotak.noonchibot.connector.AbstractTradeFeeSchemaLoader;
import com.hotak.noonchibot.connector.TradingPairSymbolRegistry;
import com.hotak.noonchibot.connector.web.RestAssistant;
import com.hotak.noonchibot.connector.web.RestRequest;
import com.hotak.noonchibot.core.IoExecutor;
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
                        .pathUrl(SpotApiSpec.COMMISSION_RATE_PATH_URL)
                        .params(Map.of("symbol", symbol))
                        .authRequired(true)
                        .build()
        );
    }

    protected TradeFeeSchema parseSchema(JsonNode body) {
        JsonNode standard = body.get("standardCommission");
        JsonNode discount = body.get("discount");

        BigDecimal makerRate = sumRates(standard.get("maker"), standard.get("buyer"));
        BigDecimal takerRate = sumRates(standard.get("taker"), standard.get("seller"));
        boolean bnbDiscountEnabled = discount.get("enabledForAccount").asBoolean()
                && discount.get("enabledForSymbol").asBoolean();
        String feeToken = bnbDiscountEnabled ? "BNB" : null;
        if (bnbDiscountEnabled) {
            // Jackson JsonNode에서는 asString(), asDecimal() 대신 asText()를 통해 파싱하는 것이 안전합니다.
            BigDecimal discountRate = new BigDecimal(discount.get("discount").asText());
            makerRate = makerRate.multiply(discountRate);
            takerRate = takerRate.multiply(discountRate);
        }
        return new TradeFeeSchema(
                feeToken,
                makerRate,
                takerRate,
                !bnbDiscountEnabled,
                List.of(),
                List.of()
        );
    }

    private BigDecimal sumRates(JsonNode... rateNodes) {
        BigDecimal sum = BigDecimal.ZERO;
        for (JsonNode node : rateNodes) {
            sum = sum.add(new BigDecimal(node.asString()));
        }
        return sum;
    }
}
