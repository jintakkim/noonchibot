package com.hotak.noonchibot.core.orderbook;

import com.hotak.noonchibot.connector.OrderBookMessageStream;
import com.hotak.noonchibot.connector.binance.BinanceApiSpec;
import com.hotak.noonchibot.connector.web.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.SmartLifecycle;
import org.springframework.http.HttpMethod;
import org.springframework.scheduling.TaskScheduler;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@RequiredArgsConstructor
public abstract class AbstractOrderBookDataSource implements OrderBookDataSource, SmartLifecycle {
    private final Map<String, Set<OrderBookMessageStream>> orderBookMessageStreams = new ConcurrentHashMap<>();
    private final TaskScheduler taskScheduler;
    private final WsAssistant wsAssistant;
    private final String publicWsUrl;
    private final ObjectMapper objectMapper;
    private final String connectionThreadName;

    private volatile boolean running = false;
    private volatile Thread connectionThread;
    protected volatile WsConnection wsConnection;

    @Override
    public OrderBook getNewOrderBook(String tradingPair) {
        OrderBook orderBook = createOrderBook();
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

    private void processConnectionLoop() {
        while (running && !Thread.currentThread().isInterrupted()) {
            try {
                this.wsConnection = wsAssistant.connect(URI.create(publicWsUrl));
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
                wsConnection.disconnect();
                this.wsConnection = null;
            }
        }
    }

    private <T extends OrderBookMessage> void castMessageToStream(String tradingPair, T message) {
        Set<OrderBookMessageStream> streams = orderBookMessageStreams.get(tradingPair);
        if(streams == null) {
            log.warn("no streams found for trading pair {}, possibly need to send unsubscribe message to exchange server", tradingPair);
            return;
        }
        streams.forEach(stream -> stream.add(message));
    }

    /**
     * 끊김으로 인한 재연결 상황 등 일때 기존 구독분을 재구독한다.
     */
    private void resubscribeIfStreamExist() {
        Set<String> tradingPairs = this.orderBookMessageStreams.keySet();
        if(tradingPairs.isEmpty()) return;
        sendSubscribe(tradingPairs);
    }

    private void processWebsocketMessages() throws InterruptedException {
        WsResponse response = wsConnection.take();  // disconnect 시 WebsocketDisconnectedException 발생
        if(response.messageType() != WsResponse.MessageType.TEXT) throw new IllegalStateException("cant handle non-text message");
        JsonNode msg = objectMapper.readTree(response.data());

        if (msg.has("error")) {
            throw new WebsocketSubscriptionFailedException(msg.get("error").toString());
        }

        //구독/해제 응답 무시
        if (msg.has("id") && msg.has("result")) {
            return;
        }

        OrderBookMessage.Type type = parseMessageType(msg);
        if(type == null) {
            processUnknownMessage(msg);
            return;
        }
        if(type == OrderBookMessage.Type.DIFF) {
            OrderBookMessage.DiffMessage diffMessage = parseDiffMessage(msg);
            castMessageToStream(diffMessage.getTradingPair(), diffMessage);
            return;
        }
        if(type == OrderBookMessage.Type.TRADE) {
            OrderBookMessage.TradeMessage tradeMessage = parseTradeMessage(msg);
            castMessageToStream(tradeMessage.getTradingPair(), tradeMessage);
        }
    }

    protected void processUnknownMessage(JsonNode msg) {
        //do noting
        //if need to process something, override this method
    }

    protected abstract OrderBook createOrderBook();
    protected abstract OrderBookMessage.SnapshotMessage getOrderBookSnapshot(String tradingPair);
    protected abstract void sendSubscribe(String tradingPair);
    protected abstract void sendSubscribe(Set<String> tradingPairs);
    protected abstract void sendUnsubscribe(String tradingPair);
    protected abstract OrderBookMessage.Type parseMessageType(JsonNode msg);
    protected abstract OrderBookMessage.DiffMessage parseDiffMessage(JsonNode msg);
    protected abstract OrderBookMessage.TradeMessage parseTradeMessage(JsonNode msg);

    @Override
    public void start() {
        running = true;
        connectionThread = Thread.ofVirtual().name(connectionThreadName).start(this::processConnectionLoop);
    }

    @Override
    public void stop() {
        running = false;
        wsConnection.disconnect();
        connectionThread.interrupt();
    }

    @Override
    public boolean isRunning() {
        return running;
    }
}
