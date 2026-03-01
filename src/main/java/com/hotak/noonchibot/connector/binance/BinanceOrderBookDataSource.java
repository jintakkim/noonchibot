package com.hotak.noonchibot.connector.binance;

import com.hotak.noonchibot.connector.OrderBookMessageStream;
import com.hotak.noonchibot.connector.TradingPairSymbolRegistry;
import com.hotak.noonchibot.connector.web.*;
import com.hotak.noonchibot.core.orderbook.OrderBook;
import com.hotak.noonchibot.core.orderbook.OrderBookDataSource;
import com.hotak.noonchibot.core.orderbook.OrderBookMessage;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpMethod;
import org.springframework.scheduling.TaskScheduler;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public class BinanceOrderBookDataSource implements OrderBookDataSource {

    @RequiredArgsConstructor
    @Getter
    private enum MessageMethod {
        SUBSCRIBE("SUBSCRIBE"), UNSUBSCRIBE("UNSUBSCRIBE");
        private final String apiValue;
    }

    private final RestAssistant restAssistant;
    private final WsAssistant wsAssistant;
    private final TradingPairSymbolRegistry tradingPairSymbolRegistry;
    private final Map<String, Set<OrderBookMessageStream>> orderBookMessageStreams;
    private final ObjectMapper objectMapper;
    private final TaskScheduler taskScheduler;

    private volatile WsConnection wsConnection;

    public BinanceOrderBookDataSource(RestAssistant restAssistant, WsAssistant wsAssistant, TradingPairSymbolRegistry tradingPairSymbolRegistry, ObjectMapper objectMapper, TaskScheduler taskScheduler) {
        this.restAssistant = restAssistant;
        this.wsAssistant = wsAssistant;
        this.tradingPairSymbolRegistry = tradingPairSymbolRegistry;
        orderBookMessageStreams = new ConcurrentHashMap<>();
        this.objectMapper = objectMapper;
        this.taskScheduler = taskScheduler;
        Thread.ofVirtual().start(this::processConnectionLoop);
    }

    @Override
    public OrderBook getNewOrderBook(String tradingPair) {
         BinanceOrderBook orderBook = new BinanceOrderBook();
         OrderBookMessage.SnapshotMessage msg = getOrderBookSnapshot(tradingPair);
         orderBook.applySnapshot(msg.getBids(), msg.getAsks(), msg.getUpdateId());
         return orderBook;
    }

    @Override
    public OrderBookMessageStream subscribe(String tradingPair) {
        Set<OrderBookMessageStream> streams = orderBookMessageStreams.computeIfAbsent(tradingPair, k -> ConcurrentHashMap.newKeySet());
        boolean firstForPair = streams.isEmpty();
        OrderBookMessageStream stream = new OrderBookMessageStream(tradingPair);
        streams.add(stream);
        if (firstForPair) {
            sendSubscribe(tradingPair);
            taskScheduler.scheduleAtFixedRate(
                    () -> castMessageToStream(tradingPair, getOrderBookSnapshot(tradingPair)),
                    Instant.now().plus(Duration.ofHours(1)),
                    Duration.ofHours(1)
            );
        }
        return stream;
    }

    private OrderBookMessage.SnapshotMessage getOrderBookSnapshot(String tradingPair) {
        String exchangeSymbol = tradingPairSymbolRegistry.convertTradingPairToExchangeSymbol(tradingPair);
        RestRequest request = RestRequest.builder()
                .method(HttpMethod.GET)
                .pathUrl(BinanceApiSpec.SNAPSHOT_PATH_URL)
                .params(Map.of("symbol", exchangeSymbol, "limit", "1000"))
                .build();
        JsonNode body = restAssistant.executeRequestAndGetJsonBody(request);
        return BinanceOrderBook.snapshotMessageFromExchange(body, tradingPair);
    }

    @Override
    public void unsubscribe(OrderBookMessageStream stream) {
        Set<OrderBookMessageStream> streams = orderBookMessageStreams.get(stream.tradingPair);
        if(streams == null) return;
        streams.remove(stream);
        if (streams.isEmpty()) {
            orderBookMessageStreams.remove(stream.tradingPair);
            sendUnsubscribe(stream.tradingPair);
        }
    }

    private void sendSubscribe(String tradingPair) {
        sendTradeRequest(MessageMethod.SUBSCRIBE, List.of(tradingPair));
        sendDiffRequest(MessageMethod.SUBSCRIBE, List.of(tradingPair));
    }

    private void sendUnsubscribe(String tradingPair) {
        sendTradeRequest(MessageMethod.UNSUBSCRIBE, List.of(tradingPair));
        sendDiffRequest(MessageMethod.UNSUBSCRIBE, List.of(tradingPair));
    }

    private void processConnectionLoop() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                this.wsConnection = wsAssistant.connect(URI.create(BinanceApiSpec.WSS_URL));
                resubscribeIfStreamExist();
                while (true) {
                    processWebsocketMessages();
                }
            } catch (WebsocketDisconnectedException wde) {
                log.warn("websocket disconnected, try reconnect after 1 seconds");
                try {
                    Thread.sleep(Duration.ofSeconds(1));
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                log.error("unexpected exception", e);
            } finally {
                this.wsConnection = null;
                wsAssistant.disconnect();
            }
        }
    }

    /**
     * 끊김으로 인한 재연결 상황 등 일때 기존 구독분을 재구독한다.
     */
    private void resubscribeIfStreamExist() {
        Set<String> tradingPairs = this.orderBookMessageStreams.keySet();
        sendDiffRequest(MessageMethod.SUBSCRIBE, tradingPairs);
        sendTradeRequest(MessageMethod.SUBSCRIBE, tradingPairs);
    }

    private void processWebsocketMessages() throws InterruptedException {
        WsResponse response = wsConnection.take();  // disconnect 시 WebsocketDisconnectedException 발생
        if(response.messageType() != WsResponse.MessageType.TEXT) throw new IllegalStateException("cant handle non-text message");
        JsonNode msg = objectMapper.readTree(response.data());
        OrderBookMessage.Type type = parseMessageType(msg);
        if(type == null) {
            processUnknownMessage(msg);
            return;
        }
        if(type == OrderBookMessage.Type.DIFF) {
            processDiffMessage(msg);
            return;
        }
        if(type == OrderBookMessage.Type.TRADE) {
            processTradeMessage(msg);
        }
    }

    private OrderBookMessage.Type parseMessageType(JsonNode msg) {
        String eventType = msg.path("e").asString();
        return switch (eventType) {
            case "depthUpdate" -> OrderBookMessage.Type.DIFF;
            case "trade" -> OrderBookMessage.Type.TRADE;
            default -> null;
        };
    }

    private void processTradeMessage(JsonNode msg) {
        String exchangeSymbol = msg.get("s").asString();
        String tradingPair = tradingPairSymbolRegistry.convertExchangeSymbolToTradingPair(exchangeSymbol);
        OrderBookMessage.TradeMessage tradeMessage = BinanceOrderBook.tradeMessageFromExchange(msg, tradingPair);
        castMessageToStream(tradingPair, tradeMessage);
    }

    private void processDiffMessage(JsonNode msg) {
        String exchangeSymbol = msg.get("s").asString();
        String tradingPair = tradingPairSymbolRegistry.convertExchangeSymbolToTradingPair(exchangeSymbol);
        OrderBookMessage.DiffMessage diffMessage = BinanceOrderBook.diffMessageFromExchange(msg, tradingPair);
        castMessageToStream(tradingPair, diffMessage);
    }

    private void processUnknownMessage(JsonNode msg) {
        //default noop
    }

    private <T extends OrderBookMessage> void castMessageToStream(String tradingPair, T message) {
        Set<OrderBookMessageStream> streams = orderBookMessageStreams.get(tradingPair);
        if(streams == null) {
            log.warn("no streams found for trading pair {}, possibly need to send unsubscribe message to exchange server", tradingPair);
            return;
        }
        streams.forEach(stream -> stream.add(message));
    }

    private void sendDiffRequest(MessageMethod method, Collection<String> tradingPairs) {
        List<String> diffMessage = tradingPairs.stream()
                .map(tradingPairSymbolRegistry::convertTradingPairToExchangeSymbol)
                .map(String::toLowerCase) //websocket need lowercase
                .map(symbol -> symbol + "@depth@100ms")
                .toList();
        wsConnection.send(new WsRequest(Map.of(
                "method", method.getApiValue(),
                "params", diffMessage,
                "id", 2
        ), false));
    }

    private void sendTradeRequest(MessageMethod method, Collection<String> tradingPairs) {
        List<String> tradeMessage = tradingPairs.stream()
                .map(tradingPairSymbolRegistry::convertTradingPairToExchangeSymbol)
                .map(String::toLowerCase) //websocket need lowercase
                .map(symbol -> symbol + "@trade")
                .toList();
        wsConnection.send(new WsRequest(Map.of(
                "method", method.getApiValue(),
                "params", tradeMessage,
                "id", 2
        ), false));
    }

    /**
     * using rest api
     */
    @Override
    public Map<String, BigDecimal> getLastTradedPrices(Set<String> tradingPairs) {
        if(tradingPairs == null || tradingPairs.isEmpty()) throw new IllegalArgumentException("한개 이상의 tradingPair가 전달되어야 합니다.");
        List<String> symbols = tradingPairs.stream()
                .map(tradingPairSymbolRegistry::convertTradingPairToExchangeSymbol)
                .toList();

        RestRequest request = RestRequest.builder()
                .method(HttpMethod.GET)
                .pathUrl(BinanceApiSpec.TICKER_PRICE_CHANGE_PATH_URL)
                .params(Map.of("symbols", symbols))
                .weightOverrides(Map.of("REQUEST_WEIGHT", BinanceApiSpec.getTickerPriceChangeDynamicWeight(symbols.size())))
                .build();

        JsonNode response = restAssistant.executeRequestAndGetJsonBody(request);

        Map<String, BigDecimal> result = new HashMap<>();
        for (JsonNode ticker : response) {
            String exchangeSymbol = ticker.get("symbol").asString();
            String tradingPair = tradingPairSymbolRegistry.convertExchangeSymbolToTradingPair(exchangeSymbol);
            result.put(tradingPair, ticker.get("price").asDecimal());
        }
        return result;
    }

    /**
     * using rest api
     */
    @Override
    public BigDecimal getLastTradedPrice(String tradingPair) {
        String exchangeSymbol = tradingPairSymbolRegistry.convertTradingPairToExchangeSymbol(tradingPair);
        RestRequest request = RestRequest.builder()
                .method(HttpMethod.GET)
                .pathUrl(BinanceApiSpec.TICKER_PRICE_CHANGE_PATH_URL)
                .params(Map.of("symbol", exchangeSymbol))
                .build();
        return restAssistant.executeRequestAndGetJsonBody(request).get("lastPrice").asDecimal();
    }
}
