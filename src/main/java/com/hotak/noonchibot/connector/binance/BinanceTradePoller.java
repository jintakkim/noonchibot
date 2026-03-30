package com.hotak.noonchibot.connector.binance;

import com.hotak.noonchibot.connector.AbstractExchangeDataPoller;
import com.hotak.noonchibot.connector.TradingPairSymbolRegistry;
import com.hotak.noonchibot.connector.web.RestAssistant;
import com.hotak.noonchibot.connector.web.RestRequest;
import com.hotak.noonchibot.core.datatype.OrderStreamStatus;
import com.hotak.noonchibot.core.datatype.TradeUpdateEvent;
import com.hotak.noonchibot.core.order.InFlightOrder;
import com.hotak.noonchibot.core.order.OrderTracker;
import com.hotak.noonchibot.core.trade.fee.TokenAmount;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.http.HttpMethod;
import tools.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.function.Consumer;

@Slf4j
public class BinanceTradePoller extends AbstractExchangeDataPoller {
    private final RestAssistant restAssistant;
    private final OrderTracker orderTracker;
    private final TradingPairSymbolRegistry tradingPairSymbolRegistry;

    public BinanceTradePoller(
            OrderStreamStatus orderStreamStatus,
            AsyncTaskExecutor taskExecutor,
            RestAssistant restAssistant,
            OrderTracker orderTracker,
            TradingPairSymbolRegistry tradingPairSymbolRegistry
            ) {
        super(orderStreamStatus, taskExecutor);
        this.restAssistant = restAssistant;
        this.orderTracker = orderTracker;
        this.tradingPairSymbolRegistry = tradingPairSymbolRegistry;
    }

    @Override
    protected void pollData() {
        executeParallel(
                orderTracker.getActiveOrders().values(),
                this::updateOrdersFills
        );
    }

    private void updateOrdersFills(InFlightOrder inFlightOrder) {
        List<TradeUpdateEvent> tradeUpdateEvents = fetchAllTradeUpdatesForOrder(inFlightOrder);
        tradeUpdateEvents.forEach(orderTracker::processTradeUpdate);
    }

    private List<TradeUpdateEvent> fetchAllTradeUpdatesForOrder(InFlightOrder inFlightOrder) {
        if (inFlightOrder.getExchangeOrderId() == null || inFlightOrder.getExchangeOrderId().equals("UNKNOWN")) {
            log.warn("exchangeOrderId가 없습니다, trade를 조회할 수 없습니다.");
            return List.of();
        }
        String symbol = tradingPairSymbolRegistry.convertTradingPairToExchangeSymbol(inFlightOrder.getTradingPair());
        RestRequest request = RestRequest.builder()
                .method(HttpMethod.GET)
                .pathUrl(BinanceApiSpec.MY_TRADES_PATH_URL)
                .params(Map.of(
                        "symbol", symbol,
                        "orderId", inFlightOrder.getExchangeOrderId()
                ))
                .authRequired(true)
                .throttlerLimitId(BinanceApiSpec.MY_TRADES_PATH_URL)
                .weightOverrides(Map.of("REQUEST_WEIGHT", 5)) //orderId 지정 요청은 5, 미지정 요청은 20(설정 기본값)
                .build();

        JsonNode trades = restAssistant.executeRequestAndGetJsonBody(request);

        List<TradeUpdateEvent> tradeUpdateEvents = new ArrayList<>();
        for (JsonNode trade : trades) {
            tradeUpdateEvents.add(parseTradeUpdate(trade, inFlightOrder.getClientOrderId(), inFlightOrder.getTradingPair()));
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
                List.of(new TokenAmount(
                        trade.get("commissionAsset").asString(),
                        trade.get("commission").asDecimal()
                )),
                trade.get("isMaker").asBoolean()
        );
    }

    private void executeParallel(Collection<InFlightOrder> inFlightOrders, Consumer<InFlightOrder> task) {
        List<Future<?>> futures = new ArrayList<>();
        for (InFlightOrder inFlightOrder : inFlightOrders) {
            futures.add(taskExecutor.submit(() -> task.accept(inFlightOrder)));
        }
        for (Future<?> future : futures) {
            try {
                future.get();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (ExecutionException e) {
                throw new RuntimeException(e.getCause());
            }
        }
    }
}
