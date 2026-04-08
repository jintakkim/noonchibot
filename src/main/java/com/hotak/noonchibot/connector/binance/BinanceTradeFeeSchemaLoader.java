package com.hotak.noonchibot.connector.binance;

import com.hotak.noonchibot.connector.TradingPairSymbolRegistry;
import com.hotak.noonchibot.connector.web.RestAssistant;
import com.hotak.noonchibot.connector.web.RestRequest;
import com.hotak.noonchibot.core.trade.fee.TradeFeeSchema;
import com.hotak.noonchibot.core.trade.fee.TradeFeeSchemaLoader;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpMethod;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RequiredArgsConstructor
public class BinanceTradeFeeSchemaLoader implements TradeFeeSchemaLoader {
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
                        .pathUrl(BinanceApiSpec.COMMISSION_RATE_PATH_URL)
                        .params(Map.of("symbol", symbol))
                        .authRequired(true)
                        .build()
        );

        JsonNode standard = body.get("standardCommission");
        JsonNode discount = body.get("discount");

        BigDecimal makerRate = sumRates(standard.get("maker"), standard.get("buyer"));
        BigDecimal takerRate = sumRates(standard.get("taker"), standard.get("seller"));

        boolean bnbDiscountEnabled = discount.get("enabledForAccount").asBoolean()
                && discount.get("enabledForSymbol").asBoolean();

        String feeToken = bnbDiscountEnabled ? "BNB" : null;

        if (bnbDiscountEnabled) {
            BigDecimal discountRate = discount.get("discount").asDecimal();
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
