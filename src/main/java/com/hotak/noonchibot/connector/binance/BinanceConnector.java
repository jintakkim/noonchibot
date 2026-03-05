package com.hotak.noonchibot.connector.binance;

import com.hotak.noonchibot.connector.*;
import com.hotak.noonchibot.connector.web.RestAssistant;
import com.hotak.noonchibot.connector.web.RestRequest;
import com.hotak.noonchibot.connector.web.TimeSynchronizer;
import com.hotak.noonchibot.core.datatype.*;
import com.hotak.noonchibot.core.order.InFlightOrder;
import com.hotak.noonchibot.core.order.OrderTracker;
import com.hotak.noonchibot.core.order.OrderType;
import com.hotak.noonchibot.core.order.OrderUpdate;
import com.hotak.noonchibot.core.orderbook.OrderBookDataSource;
import com.hotak.noonchibot.core.orderbook.OrderBookTracker;
import com.hotak.noonchibot.core.trade.fee.TokenAmount;
import com.hotak.noonchibot.core.trade.fee.TradeFeeSchemaLoader;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.scheduling.TaskScheduler;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.Future;

@Slf4j
public class BinanceConnector extends AbstractExchangeConnector {
    private static final Duration UPDATE_ORDER_STATUS_MIN_INTERVAL = Duration.ofSeconds(10);
    private static final String EXCHANGE_NAME = "binance";

    private final TimeSynchronizer timeSynchronizer;
    private Instant lastTradesPollBinanceTimestamp;

    public BinanceConnector(
            Map<String, BigDecimal> balanceLimit,
            TradeFeeSchemaLoader tradeFeeSchemaLoader,
            UserStreamTracker userStreamTracker,
            OrderIdGenerator orderIdGenerator,
            OrderBookTracker orderBookTracker,
            OrderTracker orderTracker,
            TaskScheduler taskScheduler,
            AsyncTaskExecutor taskExecutor,
            RestAssistant restAssistant,
            TradingPairSymbolRegistry tradingPairSymbolRegistry,
            OrderBookDataSource orderBookDataSource,
            TimeSynchronizer timeSynchronizer
            ) {
        super(
                EXCHANGE_NAME,
                balanceLimit,
                tradeFeeSchemaLoader,
                userStreamTracker,
                orderIdGenerator,
                orderBookTracker,
                orderTracker,
                taskScheduler,
                taskExecutor,
                restAssistant,
                BinanceApiSpec.PING_PATH_URL,
                BinanceApiSpec.EXCHANGE_INFO_PATH_URL,
                true,
                BinanceApiSpec.ORDER_ID_PREFIX,
                BinanceApiSpec.MAX_ORDER_ID_LENGTH,
                tradingPairSymbolRegistry,
                orderBookDataSource
        );
        this.timeSynchronizer = timeSynchronizer;
    }

    @Override
    public Set<OrderType> getSupportedOrderType(String tradingPair) {
        TradingRule rule = tradingRules.get(tradingPair);
        if(rule == null) throw new IllegalArgumentException("Unknown trading pair: " + tradingPair);
        return rule.supportedOrderTypes();
    }

    @Override
    protected boolean isRequestExceptionRelatedToTimeSynchronizer(Exception e) {
        String message = e.getMessage();
        return message != null
                && message.contains(String.valueOf(BinanceApiSpec.TIMESTAMP_ERROR_CODE))
                && message.contains(BinanceApiSpec.TIMESTAMP_ERROR_MESSAGE);
    }

    @Override
    protected boolean isOrderNotFoundDuringStatusUpdateException(Exception e) {
        String message = e.getMessage();
        return message != null
                && message.contains(String.valueOf(BinanceApiSpec.ORDER_NOT_EXIST_ERROR_CODE))
                && message.contains(BinanceApiSpec.ORDER_NOT_EXIST_MESSAGE);
    }

    @Override
    protected boolean isOrderNotFoundDuringCancellationException(Exception e) {
        String message = e.getMessage();
        return message != null
                && message.contains(String.valueOf(BinanceApiSpec.UNKNOWN_ORDER_ERROR_CODE))
                && message.contains(BinanceApiSpec.UNKNOWN_ORDER_MESSAGE);
    }

    /**
     * -- 바이낸스 특수 케이스 고려 --
     * 바이낸스의 경우에는 503, unknown error라면 오더 채결 여부는 미정이다.
     * 추후 api를 통해 오더 채결 여부를 확정해야한다.
     * 따라서 해당 조건일때 리턴되는 ExchangeOrderId는 "UNKNOWN" 이다.
     */
    @Override
    protected OrderPlacedDto placeOrder(String orderId, String tradingPair, BigDecimal amount, TradeType tradeType, OrderType orderType, BigDecimal price, Object... args) {
        String symbol = tradingPairSymbolRegistry.convertTradingPairToExchangeSymbol(tradingPair);
        String tradeTypeApiValue = tradeType == TradeType.BUY ? "BUY" : "SELL";
        String orderTypeApiValue = orderTypeToApiValue(orderType);

        Map<String, Object> apiParams = new HashMap<>();
        apiParams.put("symbol", symbol);
        apiParams.put("side", tradeTypeApiValue);
        apiParams.put("quantity", amount.toPlainString());
        apiParams.put("type", orderTypeApiValue);
        apiParams.put("newClientOrderId", orderId);

        if (orderType == OrderType.LIMIT || orderType == OrderType.LIMIT_MAKER) {
            apiParams.put("price", price.toPlainString());
        }
        if (orderType == OrderType.LIMIT) {
            apiParams.put("timeInForce", BinanceApiSpec.TIME_IN_FORCE_GTC);
        }
        RestRequest request = RestRequest.builder()
                .method(HttpMethod.POST)
                .pathUrl(BinanceApiSpec.ORDER_PATH_URL)
                .authRequired(true)
                .params(apiParams)
                .build();

        try {
        JsonNode orderResult = restAssistant.executeRequestAndGetJsonBody(request);
        String exchangeOrderId = orderResult.get("orderId").asString();
        Instant transactTime = Instant.ofEpochMilli(orderResult.get("transactTime").asLong());
        return new OrderPlacedDto(exchangeOrderId, transactTime);
        } catch (ExchangeApiException e) {
            if(e.httpStatusCode == HttpStatusCode.valueOf(503) && e.getMessage().contains("Unknown error, please check your request or try again later.")) {
                return new OrderPlacedDto("UNKNOWN", Instant.ofEpochMilli(timeSynchronizer.serverTime()));
            }
            throw e;
        }
    }

    @Override
    protected boolean placeCancel(String orderId, InFlightOrder trackedOrder) {
        String symbol = tradingPairSymbolRegistry.convertTradingPairToExchangeSymbol(trackedOrder.getTradingPair());

        Map<String, Object> apiParams = new HashMap<>();
        apiParams.put("symbol", symbol);
        apiParams.put("origClientOrderId", orderId);

        RestRequest request = RestRequest.builder()
                .method(HttpMethod.DELETE)
                .pathUrl(BinanceApiSpec.ORDER_PATH_URL)
                .params(apiParams)
                .authRequired(true)
                .build();
        JsonNode result = restAssistant.executeRequestAndGetJsonBody(request);
        return "CANCELED".equals(result.path("status").asString());
    }

    private static String orderTypeToApiValue(OrderType orderType) {
        return orderType.name().toUpperCase();
    }

    @Override
    protected List<TradingRule> parseTradingRule(JsonNode node) {
        List<TradingRule> rules = new ArrayList<>();
        JsonNode symbols = node.get("symbols");
        if (symbols == null || !symbols.isArray()) {
            return rules;
        }
        for (JsonNode symbol : symbols) {
            String exchangeSymbol = symbol.get("symbol").asString();
            String tradingPair = tradingPairSymbolRegistry.convertExchangeSymbolToTradingPair(exchangeSymbol);
            if (tradingPair == null) continue;
            JsonNode filters = symbol.get("filters");
            JsonNode priceFilter = findFilter(filters, "PRICE_FILTER");
            JsonNode lotSizeFilter = findFilter(filters, "LOT_SIZE");
            JsonNode minNotionalFilter = findFilter(filters, "NOTIONAL");
            Set<OrderType> orderTypes = new HashSet<>();
            for (JsonNode ot : symbol.get("orderTypes")) {
                try {
                    orderTypes.add(OrderType.valueOf(ot.asString()));
                } catch (IllegalArgumentException ignored) {
                }
            }
            rules.add(new TradingRule(
                    tradingPair,
                    lotSizeFilter.get("minQty").asDecimal(), //min order size
                    lotSizeFilter.get("maxQty").asDecimal(),
                    priceFilter.get("tickSize").asDecimal(),
                    lotSizeFilter.get("stepSize").asDecimal(),
                    minNotionalFilter.get("minNotional").asDecimal(),
                    symbol.get("quotePrecision").asInt(),
                    orderTypes,
                    tradingPair.split("-")[1],
                    tradingPair.split("-")[0]
            ));
        }
        return rules;
    }

    @Override
    protected void updateBalances() {
        Set<String> localAssetNames = new HashSet<>(accountBalances.keySet());
        RestRequest request = RestRequest.builder()
                .method(HttpMethod.GET)
                .authRequired(true)
                .pathUrl(BinanceApiSpec.ACCOUNTS_PATH_URL)
                .build();
        JsonNode accountInfo = restAssistant.executeRequestAndGetJsonBody(request);
        JsonNode balances = accountInfo.get("balances");
        Set<String> remoteAssetNames = new HashSet<>();
        for (JsonNode entry : balances) {
            String assetName = entry.get("asset").asString();
            BigDecimal free = entry.get("free").asDecimal();
            BigDecimal locked = entry.get("locked").asDecimal();
            accountAvailableBalances.put(assetName, free);
            accountBalances.put(assetName, free.add(locked));
            remoteAssetNames.add(assetName);
        }
        localAssetNames.removeAll(remoteAssetNames); //로컬 메모리에 있는 데이터와의 차집합 구하기-> 출금 등의 이벤트 발생시 로컬에는 있는데 거래소에는 없는 자산을 정리
        for (String assetName : localAssetNames) {
            accountAvailableBalances.remove(assetName);
            accountBalances.remove(assetName);
        }
    }

    @Override
    protected List<TradeUpdate> fetchAllTradeUpdatesForOrder(InFlightOrder order) {
        if (order.getExchangeOrderId() == null || order.getExchangeOrderId().equals("UNKNOWN")) {
            log.warn("exchangeOrderId가 없습니다, trade를 조회할 수 없습니다.");
            return List.of();
        }
        String symbol = tradingPairSymbolRegistry.convertTradingPairToExchangeSymbol(order.getTradingPair());
        RestRequest request = RestRequest.builder()
                .pathUrl(BinanceApiSpec.MY_TRADES_PATH_URL)
                .params(Map.of(
                        "symbol", symbol,
                        "orderId", order.getExchangeOrderId()
                ))
                .authRequired(true)
                .throttlerLimitId(BinanceApiSpec.MY_TRADES_PATH_URL)
                .weightOverrides(Map.of("REQUEST_WEIGHT", 5)) //orderId 지정 요청은 5, 미지정 요청은 20(설정 기본값)
                .build();

        JsonNode trades = restAssistant.executeRequestAndGetJsonBody(request);

        List<TradeUpdate> tradeUpdates = new ArrayList<>();
        for (JsonNode trade : trades) {
            tradeUpdates.add(parseTradeUpdate(trade, order.getClientOrderId(), order.getTradingPair()));
        }
        return tradeUpdates;
    }

    @Override
    protected OrderUpdate fetchOrderStatus(InFlightOrder order) {
        String symbol = tradingPairSymbolRegistry.convertTradingPairToExchangeSymbol(order.getTradingPair());
        RestRequest request = RestRequest.builder()
                .method(HttpMethod.GET)
                .pathUrl(BinanceApiSpec.ORDER_PATH_URL)
                .params(Map.of("symbol", symbol, "origClientOrderId", order.getClientOrderId()))
                .authRequired(true)
                .build();
        JsonNode updatedOrder = restAssistant.executeRequestAndGetJsonBody(request);
        InFlightOrder.State newState = BinanceApiSpec.ORDER_STATE.get(updatedOrder.get("status").asString());
        return new OrderUpdate(
                order.getTradingPair(),
                Instant.ofEpochMilli(updatedOrder.get("updateTime").asLong()),
                newState,
                order.getClientOrderId(),
                updatedOrder.get("orderId").asString()
        );
    }

    @Override
    protected void processUserStreamEvent(Object event) {

    }

    @Override
    protected void processUserStreamEvent(JsonNode event) {
        try {
            String eventType = event.get("e").asString();

            if ("executionReport".equals(eventType)) {
                processExecutionReport(event);
            } else if ("outboundAccountPosition".equals(eventType)) {
                processBalanceUpdate(event);
            }
        } catch (Exception e) {
            log.error("Unexpected error in user stream listener: {}", e.getMessage(), e);
        }
    }

    private void processExecutionReport(JsonNode event) {
        String executionType = event.get("x").asString();
        String clientOrderId = "CANCELED".equals(executionType)
                ? event.get("C").asString()
                : event.get("c").asString();

        if ("TRADE".equals(executionType)) {
            InFlightOrder trackedOrder = orderTracker.getFillableOrders().get(clientOrderId);
            if (trackedOrder != null) {
                BigDecimal fillQty = event.get("l").asDecimal();
                BigDecimal fillPrice = event.get("L").asDecimal();

                TradeUpdate tradeUpdate = new TradeUpdate(
                        event.get("t").asText(),
                        clientOrderId,
                        event.get("i").asText(),
                        trackedOrder.getTradingPair(),
                        Instant.ofEpochMilli(event.get("T").asLong()),
                        fillPrice,
                        fillQty,
                        fillQty.multiply(fillPrice),
                        List.of(new TokenAmount(
                                event.get("N").asText(),
                                event.get("n").asDecimal()
                        )),
                        false // WebSocket doesn't provide isMaker
                );
                orderTracker.processTradeUpdate(tradeUpdate);
            }
        }

        InFlightOrder updatableOrder = orderTracker.getUpdatableOrders().get(clientOrderId);
        if (updatableOrder != null) {
            OrderUpdate orderUpdate = new OrderUpdate(
                    updatableOrder.getTradingPair(),
                    Instant.ofEpochMilli(event.get("E").asLong()),
                    BinanceOrderStateMap.fromBinanceStatus(event.get("X").asText()),
                    clientOrderId,
                    event.get("i").asText(),
                    null
            );
            orderTracker.processOrderUpdate(orderUpdate);
        }
    }

    private void processBalanceUpdate(JsonNode event) {
        for (JsonNode balance : event.get("B")) {
            String asset = balance.get("a").asText();
            BigDecimal free = balance.get("f").asDecimal();
            BigDecimal locked = balance.get("l").asDecimal();
            accountAvailableBalances.put(asset, free);
            accountBalances.put(asset, free.add(locked));
        }
    }

    public void updateOrderFillsFromTrades() {
        if (!shouldUpdateOrderFills()) return;

        Instant queryStartTime = lastTradesPollBinanceTimestamp;
        lastTradesPollBinanceTimestamp = Instant.ofEpochMilli(timeSynchronizer.serverTime());

        Map<String, InFlightOrder> orderByExchangeIdMap = new HashMap<>();
        for (InFlightOrder order : orderTracker.getFillableOrders().values()) {
            orderByExchangeIdMap.put(order.getExchangeOrderId(), order);
        }

        List<Future<List<TradeUpdate>>> futures = new ArrayList<>();

        for (String tradingPair : getAllTradingPairs()) {
            futures.add(executor.submit(() -> fetchTradeUpdates(tradingPair, queryStartTime, orderByExchangeIdMap)));
        }

        for (Future<List<TradeUpdate>> future : futures) {
            try {
                future.get().forEach(orderTracker::processTradeUpdate);
            } catch (Exception e) {
                log.warn("Error processing trade updates: {}", e.getMessage());
            }
        }
    }

    private List<TradeUpdate> fetchTradeUpdates(String tradingPair, Instant queryStartTime,
                                                Map<String, InFlightOrder> orderByExchangeIdMap) {
        String symbol = tradingPairSymbolRegistry.convertTradingPairToExchangeSymbol(tradingPair);
        Map<String, Object> params = new HashMap<>();
        params.put("symbol", symbol);
        if (queryStartTime != null) {
            params.put("startTime", queryStartTime.toEpochMilli());
        }

        RestRequest request = RestRequest.builder()
                .method(HttpMethod.GET)
                .pathUrl(BinanceApiSpec.MY_TRADES_PATH_URL)
                .params(params)
                .authRequired(true)
                .build();

        JsonNode trades;
        try {
            trades = restAssistant.executeRequestAndGetJsonBody(request);
        } catch (Exception e) {
            log.warn("Error fetching trades for {}: {}", tradingPair, e.getMessage());
            return List.of();
        }

        List<TradeUpdate> updates = new ArrayList<>();
        for (JsonNode trade : trades) {
            String exchangeOrderId = trade.get("orderId").asString();
            if (orderByExchangeIdMap.containsKey(exchangeOrderId)) {
                InFlightOrder trackedOrder = orderByExchangeIdMap.get(exchangeOrderId);
                updates.add(parseTradeUpdate(trade, trackedOrder.getClientOrderId(), tradingPair));
            }
        }
        return updates;
    }

    private boolean shouldUpdateOrderFills() {
        long smallIntervalMs = UPDATE_ORDER_STATUS_MIN_INTERVAL.toMillis();
        long longIntervalMs = LONG_POLL_INTERVAL.toMillis();
        long smallIntervalLastTick = lastPollTimestamp.toEpochMilli() / smallIntervalMs;
        long smallIntervalCurrentTick = getCurrentTimestamp().toEpochMilli() / smallIntervalMs;
        long longIntervalLastTick = lastPollTimestamp.toEpochMilli() / longIntervalMs;
        long longIntervalCurrentTick = getCurrentTimestamp().toEpochMilli() / longIntervalMs;
        //LONG_POLL_INTERVAL 동안 폴링 없으면 반드시 조회(복구용), InFlight 상태의 주문이 있으면 UPDATE_ORDER_STATUS_MIN_INTERVAL 마다 조회
        return longIntervalCurrentTick > longIntervalLastTick || (!orderTracker.getFillableOrders().isEmpty() && smallIntervalCurrentTick > smallIntervalLastTick);
    }

    private TradeUpdate parseTradeUpdate(JsonNode trade, String clientOrderId, String tradingPair) {
        return new TradeUpdate(
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

    private JsonNode findFilter(JsonNode filters, String... filterTypes) {
        Set<String> types = Set.of(filterTypes);
        for (JsonNode filter : filters) {
            if (types.contains(filter.get("filterType").asString())) {
                return filter;
            }
        }
        throw new IllegalArgumentException("Filter not found: " + Arrays.toString(filterTypes));
    }
}
