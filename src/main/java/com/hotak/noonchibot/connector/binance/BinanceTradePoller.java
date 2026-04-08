package com.hotak.noonchibot.connector.binance;

import com.google.common.annotations.VisibleForTesting;
import com.hotak.noonchibot.connector.PollScheduler;
import com.hotak.noonchibot.connector.TradingPairSymbolRegistry;
import com.hotak.noonchibot.connector.web.RestAssistant;
import com.hotak.noonchibot.connector.web.RestRequest;
import com.hotak.noonchibot.core.IoExecutor;
import com.hotak.noonchibot.core.MainExecutor;
import com.hotak.noonchibot.core.datatype.WebsocketStatus;
import com.hotak.noonchibot.core.datatype.TradeUpdateEvent;
import com.hotak.noonchibot.core.event.ExchangeEventPublisher;
import com.hotak.noonchibot.core.order.OrderTracker;
import com.hotak.noonchibot.core.trade.fee.TokenAmount;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.SmartLifecycle;
import org.springframework.http.HttpMethod;
import org.springframework.scheduling.TaskScheduler;
import tools.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Slf4j
public class BinanceTradePoller implements SmartLifecycle {
    private final ExchangeEventPublisher eventPublisher;
    private final RestAssistant restAssistant;
    private final OrderTracker orderTracker;
    private final TradingPairSymbolRegistry tradingPairSymbolRegistry;
    private final IoExecutor ioExecutor;
    private final MainExecutor mainExecutor;
    private final PollScheduler pollScheduler;
    private final String tradePathUrl;

    private volatile boolean running = false;

    public BinanceTradePoller(
            WebsocketStatus websocketStatus,
            ExchangeEventPublisher eventPublisher,
            RestAssistant restAssistant,
            TaskScheduler taskScheduler,
            OrderTracker orderTracker,
            TradingPairSymbolRegistry tradingPairSymbolRegistry,
            IoExecutor ioExecutor,
            MainExecutor mainExecutor,
            String tradePathUrl
            ) {
        this.eventPublisher = eventPublisher;
        this.restAssistant = restAssistant;
        this.orderTracker = orderTracker;
        this.tradingPairSymbolRegistry = tradingPairSymbolRegistry;
        this.ioExecutor = ioExecutor;
        this.mainExecutor = mainExecutor;
        this.tradePathUrl = tradePathUrl;
        this.pollScheduler = new PollScheduler(websocketStatus, taskScheduler);
    }

    private record TradePollRequest(String clientOrderId, String exchangeOrderId, String tradingPair) {}

    @VisibleForTesting
    void pollData() {
        orderTracker.getAll().stream()
                .map(order -> new TradePollRequest(order.getClientOrderId(), order.getExchangeOrderId(), order.getTradingPair()))
                .forEach(request -> CompletableFuture
                        .supplyAsync(() -> fetchAllTradeUpdatesForOrder(request), ioExecutor)
                        .thenAccept(tradeEvents -> tradeEvents.forEach(eventPublisher::publish)));
    }

    private List<TradeUpdateEvent> fetchAllTradeUpdatesForOrder(TradePollRequest tradePollRequest) {
        if (tradePollRequest.exchangeOrderId == null || tradePollRequest.exchangeOrderId.equals("UNKNOWN")) {
            log.warn("exchangeOrderId가 없습니다, trade를 조회할 수 없습니다.");
            return List.of();
        }
        String symbol = tradingPairSymbolRegistry.convertTradingPairToExchangeSymbol(tradePollRequest.tradingPair);
        RestRequest request = RestRequest.builder()
                .method(HttpMethod.GET)
                .pathUrl(tradePathUrl)
                .params(Map.of(
                        "symbol", symbol,
                        "orderId", tradePollRequest.exchangeOrderId
                ))
                .authRequired(true)
                .throttlerLimitId(BinanceApiSpec.MY_TRADES_PATH_URL)
                .weightOverrides(Map.of("REQUEST_WEIGHT", 5)) //orderId 지정 요청은 5, 미지정 요청은 20(설정 기본값)
                .build();

        JsonNode trades = restAssistant.executeRequestAndGetJsonBody(request);
        List<TradeUpdateEvent> tradeUpdateEvents = new ArrayList<>();
        for (JsonNode trade : trades) {
            tradeUpdateEvents.add(parseTradeUpdate(trade, tradePollRequest.clientOrderId, tradePollRequest.tradingPair));
        }
        return tradeUpdateEvents;
    }

    private TradeUpdateEvent parseTradeUpdate(JsonNode trade, String clientOrderId, String tradingPair) {
        return new TradeUpdateEvent(
                trade.get("id").asString(),
                clientOrderId,
                trade.get("orderId").asString(),
                tradingPair,
                Instant.ofEpochMilli(trade.get("time").asLong()),
                trade.get("price").asDecimal(),
                trade.get("qty").asDecimal(),
                trade.get("quoteQty").asDecimal(),
                new TokenAmount(
                        trade.get("commissionAsset").asString(),
                        trade.get("commission").asDecimal()
                ),
                trade.get("isMaker").asBoolean()
        );
    }

    @Override
    public void start() {
        pollScheduler.start(() -> mainExecutor.execute(this::pollData));
        running = true;
    }

    @Override
    public void stop() {
        pollScheduler.stop();
        running = false;
    }

    @Override
    public boolean isRunning() {
        return running;
    }
}
