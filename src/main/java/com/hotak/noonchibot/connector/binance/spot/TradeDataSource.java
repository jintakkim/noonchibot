package com.hotak.noonchibot.connector.binance.spot;

import com.hotak.noonchibot.connector.TradingPairSymbolRegistry;
import com.hotak.noonchibot.connector.binance.BinanceExchangeErrorClassifier;
import com.hotak.noonchibot.connector.web.RestAssistant;
import com.hotak.noonchibot.connector.web.RestAssistantConfigurer;
import com.hotak.noonchibot.connector.web.RestRequest;
import com.hotak.noonchibot.core.Exchange;
import com.hotak.noonchibot.core.LifecycleAware;
import com.hotak.noonchibot.core.config.Phases;
import com.hotak.noonchibot.core.event.*;
import com.hotak.noonchibot.core.event.internal.trade.TradeEvent;
import com.hotak.noonchibot.core.resilience.CircuitBreakerNames;
import com.hotak.noonchibot.core.trade.TokenAmount;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpMethod;
import tools.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RequiredArgsConstructor
class TradeDataSource implements EventHandler<TradeEvent.UpdateRequested>, LifecycleAware {
    private final TradingPairSymbolRegistry tradingPairSymbolRegistry;
    private final RestAssistant restAssistant;
    private final EventPublisher eventPublisher;
    private final EventSubscriber eventSubscriber;
    private Subscription subscription;

    public TradeDataSource(
            TradingPairSymbolRegistry tradingPairSymbolRegistry,
            RestAssistant restAssistant,
            EventPublisher eventPublisher,
            EventSubscriber eventSubscriber,
            CircuitBreakerRegistry circuitBreakerRegistry
    ) {
        this(
                tradingPairSymbolRegistry,
                new RestAssistantConfigurer(restAssistant)
                        .circuit(circuitBreakerRegistry, CircuitBreakerNames.trades(Exchange.BINANCE_SPOT))
                        .errorClassifier(new BinanceExchangeErrorClassifier())
                        .maxRetry(2)
                        .build(),
                eventPublisher,
                eventSubscriber
        );
    }

    @Override
    public void onEvent(TradeEvent.UpdateRequested request) {
        if (request.exchangeOrderId() == null || request.exchangeOrderId().equals("UNKNOWN")) {
            throw new IllegalArgumentException("exchangeOrderId가 없습니다, trade를 조회할 수 없습니다.");
        }
        eventPublisher.publish(fetchAllTradeUpdatesForOrder(request));
    }

    private TradeEvent.Received fetchAllTradeUpdatesForOrder(TradeEvent.UpdateRequested request) {
        String symbol = tradingPairSymbolRegistry.convertTradingPairToExchangeSymbol(request.tradingPair());
        RestRequest restRequest = RestRequest.builder()
                .method(HttpMethod.GET)
                .pathUrl(ApiSpec.MY_TRADES_PATH_URL)
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
                fill.get("isMaker").asBoolean()
        );
    }


    @Override
    public void onStart() {
        subscription = eventSubscriber.subscribe(
                TradeEvent.UpdateRequested.class,
                this,
                ExecutionPolicy.concurrent()
        );
    }

    @Override
    public void onShutdown() {
        if (subscription != null) {
            subscription.close();
            subscription = null;
        }
    }

    @Override
    public int phase() {
        return Phases.TRADE_DATASOURCE_SETUP;
    }
}
