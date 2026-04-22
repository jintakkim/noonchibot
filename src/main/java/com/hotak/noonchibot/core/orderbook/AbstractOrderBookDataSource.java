package com.hotak.noonchibot.core.orderbook;

import com.hotak.noonchibot.connector.web.*;
import com.hotak.noonchibot.core.AbstractWebsocketDataSource;
import com.hotak.noonchibot.core.IoExecutor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.TaskScheduler;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public abstract class AbstractOrderBookDataSource extends AbstractWebsocketDataSource implements OrderBookDataSource {
    private final Map<String, Set<OrderBookMessageStream>> orderBookMessageStreams = new ConcurrentHashMap<>();
    protected final TaskScheduler taskScheduler;
    private final boolean isDex;

    public AbstractOrderBookDataSource(
            WsAssistant wsAssistant,
            String wsUrl,
            ObjectMapper objectMapper,
            IoExecutor ioExecutor,
            TaskScheduler taskScheduler,
            boolean isDex
    ) {
        super(wsAssistant, wsUrl, objectMapper, ioExecutor);
        this.taskScheduler = taskScheduler;
        this.isDex = isDex;
    }

    @Override
    public OrderBook getNewOrderBook(String tradingPair) {
        OrderBook orderBook = new OrderBook(isDex);
        OrderBookMessage.SnapshotMessage msg = getOrderBookSnapshot(tradingPair);
        orderBook.applySnapshot(msg.getBids(), msg.getAsks(), msg.getUpdateId());
        return orderBook;
    }

    @Override
    public OrderBookMessageStream subscribeOrderBookStream(String tradingPair) {
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

    @Override
    protected void processMessage() throws InterruptedException {
        WsResponse response = wsConnection.take();  // disconnect 시 WebsocketDisconnectedException 발생
        if(response.messageType() != WsResponse.MessageType.TEXT) throw new IllegalStateException("cant handle non-text message");
        JsonNode msg = objectMapper.readTree(response.data());

        if (isErrorMessage(msg)) {
            throw new WebsocketSubscriptionFailedException(msg.toString());
        }
        //구독/해제 응답 무시
        if(isAckMessage(msg)) return;

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

    protected abstract OrderBookMessage.SnapshotMessage getOrderBookSnapshot(String tradingPair);
    protected abstract void sendSubscribe(String tradingPair);
    protected abstract void sendSubscribe(Set<String> tradingPairs);
    protected abstract void sendUnsubscribe(String tradingPair);
    protected abstract boolean isErrorMessage(JsonNode msg);
    protected abstract boolean isAckMessage(JsonNode msg);
    protected abstract OrderBookMessage.Type parseMessageType(JsonNode msg);
    protected abstract OrderBookMessage.DiffMessage parseDiffMessage(JsonNode msg);
    protected abstract OrderBookMessage.TradeMessage parseTradeMessage(JsonNode msg);

    @Override
    protected void onConnected() {
        resubscribeIfStreamExist();
    }
}
