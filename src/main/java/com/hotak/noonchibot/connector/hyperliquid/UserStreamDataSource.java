package com.hotak.noonchibot.connector.hyperliquid;

import com.hotak.noonchibot.connector.TradingPairSymbolRegistry;
import com.hotak.noonchibot.connector.web.*;
import com.hotak.noonchibot.core.AbstractWebsocketDataSource;
import com.hotak.noonchibot.core.config.Phases;
import com.hotak.noonchibot.core.derivative.PositionSide;
import com.hotak.noonchibot.core.event.EventPublisher;
import com.hotak.noonchibot.core.event.internal.order.OrderEvent;
import com.hotak.noonchibot.core.event.internal.derivative.PositionEvent;
import com.hotak.noonchibot.core.event.internal.trade.TradeEvent;
import com.hotak.noonchibot.core.order.OrderState;
import com.hotak.noonchibot.core.trade.TokenAmount;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.TaskScheduler;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.time.Duration;
import java.util.List;
import java.util.Map;

@Slf4j
class UserStreamDataSource extends AbstractWebsocketDataSource {
    private static final Duration HEARTBEAT_INTERVAL = Duration.ofSeconds(30);
    private final String userAddress;
    private final TradingPairSymbolRegistry tradingPairSymbolRegistry;
    private final EventPublisher eventPublisher;
    private final String websocketUrl;

    public UserStreamDataSource(
            WsAssistant wsAssistant,
            ObjectMapper objectMapper,
            TaskScheduler taskScheduler,
            ApplicationEventPublisher applicationEventPublisher,
            String userAddress,
            TradingPairSymbolRegistry tradingPairSymbolRegistry,
            EventPublisher eventPublisher,
            String websocketUrl
    ) {
        super(wsAssistant, objectMapper, taskScheduler, applicationEventPublisher);
        this.userAddress = userAddress;
        this.tradingPairSymbolRegistry = tradingPairSymbolRegistry;
        this.eventPublisher = eventPublisher;
        this.websocketUrl = websocketUrl;
    }

    @Override
    protected URI connectionUri() {
        return URI.create(websocketUrl);
    }

    @Override
    protected Duration heartbeatInterval() {
        return HEARTBEAT_INTERVAL;
    }

    @Override
    protected void sendHeartbeat(WsConnection connection) {
        connection.send(new WsRequest(Map.of("method", "ping"), false));
    }

    @Override
    protected void handleConnected() {
        subscribe("orderUpdates");
        subscribe("userFills");
        subscribe("clearinghouseState");
    }

    @Override
    protected WebsocketMessageResult processMessage(WsResponse wsResponse) {
        if (wsResponse.messageType() != WsResponse.MessageType.TEXT) {
            throw new IllegalStateException("cant handle non-text message");
        }
        JsonNode root = objectMapper.readTree(wsResponse.data());
        String channel = root.path("channel").asString();
        if ("subscriptionResponse".equals(channel) || "pong".equals(channel)) {
            return WebsocketMessageResult.acknowledged();
        }
        switch (channel) {
            case "orderUpdates" -> handleOrderUpdates(root.path("data"));
            case "userFills" -> handleUserFills(root.path("data"));
            case "clearinghouseState" -> handleClearinghouseState(root.path("data"));
            default -> {
                return WebsocketMessageResult.ignored("No handler for channel " + channel + ": " + root);
            }
        }
        return WebsocketMessageResult.processed();
    }

    private void subscribe(String type) {
        wsConnection.send(new WsRequest(Map.of(
                "method", "subscribe",
                "subscription", Map.of(
                        "type", type,
                        "user", userAddress
                )
        ), false));
    }

    private void handleOrderUpdates(JsonNode data) {
        for (JsonNode entry : data) {
            JsonNode order = entry.get("order");
            OrderState state = DerivativeApiSpec.ORDER_STATE.get(entry.get("status").asString());
            if (state == null) {
                log.debug("Unmapped order status: {}", entry.get("status").asString());
                continue;
            }
            JsonNode cloid = order.path("cloid");
            if (cloid.isMissingNode() || cloid.isNull()) {
                continue;
            }
            String tradingPair = tradingPairSymbolRegistry.convertExchangeSymbolToTradingPair(order.get("coin").asString());
            eventPublisher.publish(new OrderEvent.StatusReceived(
                    tradingPair,
                    cloid.asString(),
                    String.valueOf(order.get("oid").asLong()),
                    state,
                    Instant.ofEpochMilli(entry.get("statusTimestamp").asLong())
            ));
        }
    }

    private void handleUserFills(JsonNode data) {
        if (data.has("isSnapshot") && data.get("isSnapshot").asBoolean()) {
            return;
        }
        for (JsonNode fill : data.path("fills")) {
            String tradingPair = tradingPairSymbolRegistry.convertExchangeSymbolToTradingPair(fill.get("coin").asString());
            BigDecimal price = fill.get("px").asDecimal();
            BigDecimal size = fill.get("sz").asDecimal();
            String exchangeOrderId = String.valueOf(fill.get("oid").asLong());
            eventPublisher.publish(new TradeEvent.Received(
                    null,
                    exchangeOrderId,
                    tradingPair,
                    List.of(new TradeEvent.Fill(
                            fill.get("tid").asString(),
                            Instant.ofEpochMilli(fill.get("time").asLong()),
                            price,
                            size,
                            price.multiply(size),
                            new TokenAmount(fill.get("feeToken").asString(), fill.get("fee").asDecimal()),
                            !fill.get("crossed").asBoolean()
                    ))
            ));
        }
    }

    private void handleClearinghouseState(JsonNode data) {
        Instant timestamp = Instant.now();
        for (JsonNode assetPosition : data.path("assetPositions")) {
            JsonNode position = assetPosition.get("position");
            BigDecimal amount = position.get("szi").asDecimal();
            if (amount.signum() == 0) continue;
            PositionSide side = amount.signum() < 0 ? PositionSide.SHORT : PositionSide.LONG;
            String tradingPair = tradingPairSymbolRegistry.convertExchangeSymbolToTradingPair(position.get("coin").asString());
            eventPublisher.publish(new PositionEvent.UpdateReceived(
                    tradingPair,
                    side,
                    amount,
                    position.get("entryPx").asDecimal(),
                    position.get("unrealizedPnl").asDecimal(),
                    timestamp
            ));
        }
    }

    @Override
    public int phase() {
        return Phases.USER_STREAM_DATASOURCE_SETUP;
    }
}
