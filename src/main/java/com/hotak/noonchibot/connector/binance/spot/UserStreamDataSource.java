package com.hotak.noonchibot.connector.binance.spot;

import com.hotak.noonchibot.connector.TradingPairSymbolRegistry;
import com.hotak.noonchibot.connector.binance.BinanceAuthenticator;
import com.hotak.noonchibot.connector.web.*;
import com.hotak.noonchibot.core.AbstractWebsocketDataSource;
import com.hotak.noonchibot.core.IoExecutor;
import com.hotak.noonchibot.core.config.Phases;
import com.hotak.noonchibot.core.balance.AssetState;
import com.hotak.noonchibot.core.event.EventPublisher;
import com.hotak.noonchibot.core.event.internal.balance.BalanceEvent;
import com.hotak.noonchibot.core.event.internal.order.OrderEvent;
import com.hotak.noonchibot.core.event.internal.trade.TradeEvent;
import com.hotak.noonchibot.core.trade.TokenAmount;
import lombok.extern.slf4j.Slf4j;
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

    public UserStreamDataSource(
            WsAssistant wsAssistant,
            ObjectMapper objectMapper,
            BinanceAuthenticator binanceAuthenticator,
            TradingPairSymbolRegistry tradingPairSymbolRegistry,
            EventPublisher eventPublisher,
            IoExecutor ioExecutor
    ) {
        super(wsAssistant, objectMapper, ioExecutor);
        this.binanceAuthenticator = binanceAuthenticator;
        this.tradingPairSymbolRegistry = tradingPairSymbolRegistry;
        this.eventPublisher = eventPublisher;
    }

    @Override
    protected URI connectionUri() {
        return URI.create(ApiSpec.WSS_API_URL);
    }

    @Override
    protected void onConnected() {
        subscribeUserStream();
    }

    @Override
    protected void processMessage(WsResponse response) {
        if (response.messageType() != WsResponse.MessageType.TEXT) {
            throw new IllegalStateException("cant handle non-text message");
        }

        JsonNode eventMessage = objectMapper.readTree(response.data());
        if (eventMessage.has("id") && eventMessage.has("status")) return;
        if (eventMessage.has("event") && eventMessage.has("subscriptionId")) {
            eventMessage = eventMessage.get("event");
        }
        if ("eventStreamTerminated".equals(eventMessage.path("e").asString())) {
            throw new WebsocketDisconnectedException("Stream terminated by server");
        }

        String eventType = eventMessage.path("e").asString();
        switch (eventType) {
            case "executionReport" -> processExecutionReport(eventMessage);
            case "outboundAccountPosition" -> processBalanceUpdate(eventMessage);
            default -> log.debug("unknown user stream event: {}", eventMessage);
        }
    }

    private void subscribeUserStream() {
        Map<String, Object> params = binanceAuthenticator.generateWsSubscribeParams();
        String requestId = UUID.randomUUID().toString();

        wsConnection.send(new WsRequest(Map.of(
                "id", requestId,
                "method", "userDataStream.subscribe.signature",
                "params", params
        ), false));

        try {
            WsResponse response = wsConnection.take();
            JsonNode data = objectMapper.readTree(response.data());
            if (data.path("status").asInt() != 200) {
                throw new WebsocketSubscriptionFailedException("Error subscribing to user stream: " + data);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new WebsocketDisconnectedException("Interrupted while subscribing user stream");
        }
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
