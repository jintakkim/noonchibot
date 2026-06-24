package com.hotak.noonchibot.connector.binance.derivative;

import com.hotak.noonchibot.connector.TradingPairSymbolRegistry;
import com.hotak.noonchibot.connector.web.RestAssistant;
import com.hotak.noonchibot.connector.web.RestRequest;
import com.hotak.noonchibot.core.event.EventHandler;
import com.hotak.noonchibot.core.event.EventPublisher;
import com.hotak.noonchibot.core.event.internal.trade.TradeEvent;
import com.hotak.noonchibot.core.trade.TokenAmount;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpMethod;
import tools.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@RequiredArgsConstructor
public class TradeDataSource implements EventHandler<TradeEvent.UpdateRequest> {
    private final TradingPairSymbolRegistry tradingPairSymbolRegistry;
    private final RestAssistant restAssistant;
    private final EventPublisher eventPublisher;

    @Override
    public void onEvent(TradeEvent.UpdateRequest request) {
        if (request.exchangeOrderId() == null || request.exchangeOrderId().equals("UNKNOWN")) {
            throw new IllegalArgumentException("exchangeOrderId가 없습니다, trade를 조회할 수 없습니다.");
        }
        eventPublisher.publish(fetchAllTradeUpdatesForOrder(request));
    }

    private TradeEvent.Received fetchAllTradeUpdatesForOrder(TradeEvent.UpdateRequest request) {
        String symbol = tradingPairSymbolRegistry.convertTradingPairToExchangeSymbol(request.tradingPair());
        RestRequest restRequest = RestRequest.builder()
                .method(HttpMethod.GET)
                .pathUrl(ApiSpec.TRADE_PATH_URL)
                .params(Map.of(
                        "symbol", symbol,
                        "orderId", request.exchangeOrderId()
                ))
                .authRequired(true)
                .build();

        JsonNode trades = restAssistant.executeRequestAndGetJsonBody(restRequest);
        List<TradeEvent.Fill> fills = new ArrayList<>();
        for (JsonNode fill : trades) {
            fills.add(parseTradeFill(fill));
        }
        return new TradeEvent.Received(
                request.clientOrderId(),
                request.exchangeOrderId(),
                request.tradingPair(),
                fills
        );
    }

    private TradeEvent.Fill parseTradeFill(JsonNode fill) {
        return new TradeEvent.Fill(
                fill.get("id").asString(),
                Instant.ofEpochMilli(fill.get("time").asLong()),
                fill.get("price").asDecimal(),
                fill.get("qty").asDecimal(),
                fill.get("quoteQty").asDecimal(),
                new TokenAmount(
                        fill.get("commissionAsset").asString(),
                        fill.get("commission").asDecimal()
                ),
                fill.get("maker").asBoolean()
        );
    }
}
