package com.hotak.noonchibot.connector.binance.spot;

import com.hotak.noonchibot.connector.TradingPairSymbolRegistry;
import com.hotak.noonchibot.connector.binance.BinanceAuthenticator;
import com.hotak.noonchibot.connector.web.*;
import com.hotak.noonchibot.core.AbstractWebsocketDataSource;
import com.hotak.noonchibot.core.config.Phases;
import com.hotak.noonchibot.core.balance.AssetState;
import com.hotak.noonchibot.core.event.EventPublisher;
import com.hotak.noonchibot.core.event.internal.balance.BalanceEvent;
import com.hotak.noonchibot.core.event.internal.order.OrderEvent;
import com.hotak.noonchibot.core.event.internal.trade.TradeEvent;
import com.hotak.noonchibot.core.trade.TokenAmount;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.TaskScheduler;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
class UserStreamDataSource extends AbstractWebsocketDataSource {
    private final BinanceAuthenticator binanceAuthenticator;
    private final TradingPairSymbolRegistry tradingPairSymbolRegistry;
    private final EventPublisher eventPublisher;
    private final String websocketApiUrl;
    private volatile String pendingSubscriptionRequestId;

    public UserStreamDataSource(
            WsAssistant wsAssistant,
            ObjectMapper objectMapper,
            BinanceAuthenticator binanceAuthenticator,
            TradingPairSymbolRegistry tradingPairSymbolRegistry,
            EventPublisher eventPublisher,
            TaskScheduler taskScheduler,
            ApplicationEventPublisher applicationEventPublisher,
            String websocketApiUrl
    ) {
        super(wsAssistant, objectMapper, taskScheduler, applicationEventPublisher);
        this.binanceAuthenticator = binanceAuthenticator;
        this.tradingPairSymbolRegistry = tradingPairSymbolRegistry;
        this.eventPublisher = eventPublisher;
        this.websocketApiUrl = websocketApiUrl;
    }

    @Override
    protected URI connectionUri() {
        return URI.create(websocketApiUrl);
    }

    @Override
    protected void handleConnected() {
        subscribeUserStream();
    }

    @Override
    protected WebsocketMessageResult processMessage(WsResponse response) {
        if (response.messageType() != WsResponse.MessageType.TEXT) {
            throw new IllegalStateException("cant handle non-text message");
        }

        JsonNode eventMessage = objectMapper.readTree(response.data());
        if (eventMessage.has("id") && eventMessage.has("status")) {
            String responseId = eventMessage.path("id").asString();
            if (!responseId.equals(pendingSubscriptionRequestId)) {
                return WebsocketMessageResult.ignored("Unmatched command response: " + eventMessage);
            }

            pendingSubscriptionRequestId = null;
            if (eventMessage.path("status").asInt() != 200) {
                int errorCode = eventMessage.path("error").path("code").asInt();
                if (errorCode == -2014 || errorCode == -2015) {
                    return WebsocketMessageResult.reconnect(
                            "Error authenticating user stream: " + eventMessage);
                }
                return WebsocketMessageResult.reconnect(
                        "Error subscribing to user stream: " + eventMessage);
            }
            return WebsocketMessageResult.acknowledged();
        }
        if (eventMessage.has("event") && eventMessage.has("subscriptionId")) {
            eventMessage = eventMessage.get("event");
        }
        if ("eventStreamTerminated".equals(eventMessage.path("e").asString())) {
            return WebsocketMessageResult.reconnect("Stream terminated by server");
        }

        String eventType = eventMessage.path("e").asString();
        switch (eventType) {
            case "executionReport" -> {
                processExecutionReport(eventMessage);
                return WebsocketMessageResult.processed();
            }
            case "outboundAccountPosition" -> {
                processBalanceUpdate(eventMessage);
                return WebsocketMessageResult.processed();
            }
            default -> {
                return WebsocketMessageResult.ignored("Unknown user stream event: " + eventMessage);
            }
        }
    }

    private void subscribeUserStream() {
        Map<String, Object> params = binanceAuthenticator.generateWsSubscribeParams();
        String requestId = UUID.randomUUID().toString();
        pendingSubscriptionRequestId = requestId;

        wsConnection.send(new WsRequest(Map.of(
                "id", requestId,
                "method", "userDataStream.subscribe.signature",
                "params", params
        ), false));
    }

    private void processExecutionReport(JsonNode eventMessage) {
        String executionType = eventMessage.get("x").asString();
        String clientOrderId = "CANCELED".equals(executionType)
                ? eventMessage.get("C").asString()
                : eventMessage.get("c").asString();
        String exchangeOrderId = eventMessage.get("i").asString();
        String tradingPair = tradingPairSymbolRegistry
                .convertExchangeSymbolToTradingPair(eventMessage.get("s").asString());

        publishTradeIfFilled(eventMessage, clientOrderId, exchangeOrderId, tradingPair);

        eventPublisher.publish(new OrderEvent.StatusReceived(
                tradingPair,
                clientOrderId,
                exchangeOrderId,
                ApiSpec.ORDER_STATE.get(eventMessage.get("X").asString()),
                Instant.ofEpochMilli(eventMessage.get("E").asLong())
        ));
    }

    private void publishTradeIfFilled(
            JsonNode orderMessage,
            String clientOrderId,
            String exchangeOrderId,
            String tradingPair
    ) {
        String tradeId = orderMessage.get("t").asString();
        if ("-1".equals(tradeId)) return;

        BigDecimal fillPrice = orderMessage.get("L").asDecimal();
        BigDecimal fillBaseAmount = orderMessage.get("l").asDecimal();
        BigDecimal fillQuoteAmount = fillPrice.multiply(fillBaseAmount);

        String feeToken = orderMessage.path("N").asString();
        BigDecimal feeAmount = orderMessage.path("n").asDecimal();
        TokenAmount fee = feeToken == null || feeToken.isBlank()
                ? null
                : new TokenAmount(feeToken, feeAmount);

        eventPublisher.publish(new TradeEvent.Received(
                clientOrderId,
                exchangeOrderId,
                tradingPair,
                List.of(new TradeEvent.Fill(
                        tradeId,
                        Instant.ofEpochMilli(orderMessage.get("T").asLong()),
                        fillPrice,
                        fillBaseAmount,
                        fillQuoteAmount,
                        fee,
                        orderMessage.get("m").asBoolean()
                ))
        ));
    }

    private void processBalanceUpdate(JsonNode eventMessage) {
        Instant timestamp = Instant.ofEpochMilli(eventMessage.get("E").asLong());
        Map<String, AssetState> updates = new HashMap<>();
        for (JsonNode balance : eventMessage.path("B")) {
            String asset = balance.get("a").asString();
            BigDecimal available = balance.get("f").asDecimal();
            BigDecimal locked = balance.get("l").asDecimal();
            updates.put(asset, new AssetState(available.add(locked), available, timestamp));
        }
        eventPublisher.publish(new BalanceEvent.UpdateReceived(updates, timestamp));
    }

    @Override
    public int phase() {
        return Phases.USER_STREAM_DATASOURCE_SETUP;
    }
}
