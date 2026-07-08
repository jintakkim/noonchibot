package com.hotak.noonchibot.core.orderbook;

import com.hotak.noonchibot.connector.web.*;
import com.hotak.noonchibot.core.AbstractWebsocketDataSource;
import com.hotak.noonchibot.core.IoExecutor;
import com.hotak.noonchibot.core.LifecycleAware;
import com.hotak.noonchibot.core.config.Phases;
import com.hotak.noonchibot.core.event.*;
import com.hotak.noonchibot.core.event.internal.orderbook.OrderBookEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.TaskScheduler;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

@Slf4j
public abstract class AbstractOrderBookDataSource extends AbstractWebsocketDataSource implements LifecycleAware {
    protected final TaskScheduler taskScheduler;
    private final EventPublisher eventPublisher;
    private final EventSubscriber eventSubscriber;
    /**
     * 현제는 unsub , 구독자 카운팅 기능이 없다
     * -> 해당 데이터 소스 사용처가 orderBookTracker 한 곳이기 떄문에 실질적으로 필요가 없다.
     * -> unsub되는 상황이 프로그램 종료시 제외하고 없기 떄문에 구현 x
     */
    private final Set<String> subscribedPairs = ConcurrentHashMap.newKeySet();
    private volatile ScheduledFuture<?> snapshotRefreshTask;
    private final Set<Subscription> handlerSubscriptions = new HashSet<>();


    public AbstractOrderBookDataSource(
            WsAssistant wsAssistant,
            ObjectMapper objectMapper,
            IoExecutor ioExecutor,
            TaskScheduler taskScheduler,
            EventPublisher eventPublisher,
            EventSubscriber eventSubscriber
    ) {
        super(wsAssistant, objectMapper, ioExecutor);
        this.taskScheduler = taskScheduler;
        this.eventPublisher = eventPublisher;
        this.eventSubscriber = eventSubscriber;
    }

    void trackingOrderBook(OrderBookEvent.TrackingRequested req) {
        if(!isConnected()) {
            log.warn("ws not connected");
            return;
        }
        String tradingPair = req.tradingPair();
        if (!subscribedPairs.add(tradingPair)) {
            log.warn("already subscribed: {}", tradingPair);
            return;
        }
        try {
            sendSubscribe(tradingPair);
            publishSnapshot(tradingPair);
        } catch (Exception e) {
            subscribedPairs.remove(tradingPair);
            throw e;
        }
    }

    /**
     * 연결이 종료되고 재연결시 기존 구독분을 다시 연결한다.
     *
     */
    @Override
    protected void onConnected() {
        if (subscribedPairs.isEmpty()) return;
        sendSubscribe(subscribedPairs);
        refreshAllSnapshots();
    }

    private void refreshAllSnapshots() {
        subscribedPairs.forEach(this::publishSnapshot);
    }

    private void publishSnapshot(String tradingPair) {
        eventPublisher.publish(fetchOrderBookSnapshot(tradingPair));
    }

    @Override
    protected void processMessage(WsResponse response) {
        if (response.messageType() != WsResponse.MessageType.TEXT) {
            throw new IllegalStateException("cant handle non-text message");
        }
        JsonNode msg = objectMapper.readTree(response.data());

        if (isErrorMessage(msg)) {
            throw new WebSocketErrorMessageReceivedException(msg.toString());
        }
        if (isAckMessage(msg)) return;

        OrderBookMessage.Type type = parseMessageType(msg);
        if (type == null) {
            processUnknownMessage(msg);
            return;
        }
        switch (type) {
            case DIFF -> eventPublisher.publish(parseWsDiffMessage(msg));
            case TRADE -> parseWsTradeMessage(msg).forEach(eventPublisher::publish);
            case SNAPSHOT -> eventPublisher.publish(parseWsSnapshotMessage(msg));
        }
    }

    protected void processUnknownMessage(JsonNode msg) {
        //do noting
        //if need to process something, override this method
    }
    // rest api
    protected abstract OrderBookEvent.SnapshotReceived fetchOrderBookSnapshot(String tradingPair);

    // ws
    protected abstract OrderBookMessage.Type parseMessageType(JsonNode msg);
    protected abstract void sendSubscribe(String tradingPair);
    protected abstract void sendSubscribe(Set<String> tradingPairs);
    protected abstract void sendUnsubscribe(String tradingPair);
    protected abstract boolean isErrorMessage(JsonNode msg);
    protected abstract boolean isAckMessage(JsonNode msg);
    protected abstract OrderBookEvent.DiffReceived parseWsDiffMessage(JsonNode msg);
    protected abstract List<OrderBookEvent.TradeReceived> parseWsTradeMessage(JsonNode msg);
    protected abstract OrderBookEvent.SnapshotReceived parseWsSnapshotMessage(JsonNode msg);

    @Override
    public void onStart() {
        super.onStart();
        handlerSubscriptions.add(
                eventSubscriber.subscribe(
                OrderBookEvent.TrackingRequested.class,
                this::trackingOrderBook,
                ExecutionPolicy.concurrent()
                ));
        snapshotRefreshTask = taskScheduler.scheduleAtFixedRate(
                this::refreshAllSnapshots,
                Duration.ofSeconds(30)
        );
    }

    @Override
    public int phase() {
        return Phases.ORDER_BOOK_DATASOURCE_SETUP;
    }

    @Override
    public void onShutdown() {
        super.onShutdown();
        if(snapshotRefreshTask != null) {
            snapshotRefreshTask.cancel(true);
            snapshotRefreshTask = null;
        }
        handlerSubscriptions.forEach(Subscription::close);
        handlerSubscriptions.clear();
        subscribedPairs.clear();
    }
}
