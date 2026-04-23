package com.hotak.noonchibot.connector.bybit;

import com.google.common.annotations.VisibleForTesting;
import com.hotak.noonchibot.connector.LifecycleComponent;
import com.hotak.noonchibot.connector.PollScheduler;
import com.hotak.noonchibot.connector.TradingPairSymbolRegistry;
import com.hotak.noonchibot.connector.web.RestAssistant;
import com.hotak.noonchibot.connector.web.RestRequest;
import com.hotak.noonchibot.core.IoExecutor;
import com.hotak.noonchibot.core.MainExecutor;
import com.hotak.noonchibot.core.datatype.TradeUpdateEvent;
import com.hotak.noonchibot.core.datatype.WebsocketStatus;
import com.hotak.noonchibot.core.event.ExchangeEventPublisher;
import com.hotak.noonchibot.core.order.OrderTracker;
import com.hotak.noonchibot.core.trade.fee.TokenAmount;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpMethod;
import org.springframework.scheduling.TaskScheduler;
import tools.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Slf4j
class SpotTradePoller implements LifecycleComponent {
    private final ExchangeEventPublisher eventPublisher;
    private final RestAssistant restAssistant;
    private final OrderTracker orderTracker;
    private final TradingPairSymbolRegistry tradingPairSymbolRegistry;
    private final IoExecutor ioExecutor;
    private final MainExecutor mainExecutor;
    private final PollScheduler pollScheduler;
    private final String tradePathUrl;

    public SpotTradePoller(
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
                        "category", "spot",
                        "symbol", symbol,
                        "orderId", tradePollRequest.exchangeOrderId,
                        // Bybit은 trade 메시지를 받을 때 페이지네이션을 이용한다.
                        // 디폴트는 50이나 안전하게 100으로 요청
                        "limit", "100"
                ))
                .authRequired(true)
                .throttlerLimitId(SpotApiSpec.MY_TRADES_PATH_URL)
                .build();

        JsonNode responseBody = restAssistant.executeRequestAndGetJsonBody(request);

        List<TradeUpdateEvent> tradeUpdateEvents = new ArrayList<>();
        JsonNode resultNode = responseBody.get("result");

        for (JsonNode trade : resultNode.get("list")) {
            tradeUpdateEvents.add(parseTradeUpdate(trade, tradePollRequest.clientOrderId, tradePollRequest.tradingPair));
        }

        return tradeUpdateEvents;
    }

    private TradeUpdateEvent parseTradeUpdate(JsonNode trade, String clientOrderId, String tradingPair) {
        return new TradeUpdateEvent(
                trade.get("execId").asString(),
                clientOrderId,
                trade.get("orderId").asString(),
                tradingPair,
                Instant.ofEpochMilli(trade.get("execTime").asLong()),
                trade.get("execPrice").asDecimal(),
                trade.get("execQty").asDecimal(),
                trade.get("execValue").asDecimal(),
                new TokenAmount(
                        trade.get("feeCurrency").asString(),
                        trade.get("execFee").asDecimal()
                ),
                trade.get("isMaker").asBoolean()
        );
    }

    @Override
    public void start() {
        pollScheduler.start(() -> mainExecutor.execute(this::pollData));
    }

    @Override
    public void shutdown() {
        pollScheduler.stop();
    }
}
