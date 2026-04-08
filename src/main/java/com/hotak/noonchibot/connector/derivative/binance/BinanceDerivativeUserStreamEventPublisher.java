package com.hotak.noonchibot.connector.derivative.binance;

import com.hotak.noonchibot.connector.TradingPairSymbolRegistry;
import com.hotak.noonchibot.connector.web.*;
import com.hotak.noonchibot.core.IoExecutor;
import com.hotak.noonchibot.core.datatype.TradeUpdateEvent;
import com.hotak.noonchibot.core.datatype.WebsocketStatus;
import com.hotak.noonchibot.core.derivative.PositionSide;
import com.hotak.noonchibot.core.event.BalanceUpdateEvent;
import com.hotak.noonchibot.core.event.ExchangeEventPublisher;
import com.hotak.noonchibot.core.event.OrderUpdateEvent;
import com.hotak.noonchibot.core.event.PositionUpdateEvent;
import com.hotak.noonchibot.core.trade.fee.TokenAmount;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.SmartLifecycle;
import org.springframework.http.HttpMethod;
import org.springframework.scheduling.TaskScheduler;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledFuture;

import static java.lang.Thread.sleep;

@Slf4j
@RequiredArgsConstructor
public class BinanceDerivativeUserStreamEventPublisher implements SmartLifecycle, WebsocketStatus {
    private static final Duration LISTEN_KEY_KEEP_ALIVE_INTERVAL = Duration.ofMinutes(45);

    private final RestAssistant restAssistant;
    private final WsAssistant wsAssistant;
    private final TaskScheduler taskScheduler;
    private final ObjectMapper objectMapper;
    private final TradingPairSymbolRegistry tradingPairSymbolRegistry;
    private final ExchangeEventPublisher exchangeEventPublisher;
    private final IoExecutor ioExecutor;

    private volatile boolean running = false;
    private volatile ScheduledFuture<?> scheduledFuture;
    private volatile WsConnection wsConnection;
    private volatile Future<?> connectionFuture;
    private volatile Instant lastRecvTime;

    private String fetchListenKey() {
        RestRequest request = RestRequest.builder()
                .method(HttpMethod.POST)
                .pathUrl(BinanceDerivativeApiSpec.LISTEN_KEY_PATH_URL)
                .authRequired(true)
                .build();
        return restAssistant.executeRequestAndGetJsonBody(request).get("listenKey").asString();
    }

    private void renewListenKey() {
        RestRequest request = RestRequest.builder()
                .method(HttpMethod.PUT)
                .pathUrl(BinanceDerivativeApiSpec.LISTEN_KEY_PATH_URL)
                .authRequired(true)
                .build();
        restAssistant.executeRequestAndGetResponse(request);
    }

    private void connectionLoop() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                String listenKey = fetchListenKey();
                this.wsConnection = wsAssistant.connect(URI.create(BinanceDerivativeApiSpec.WSS_PRIVATE_URL + "/" + listenKey));
                while (true) {
                    WsResponse response = wsConnection.take();
                    processMessage(response.data());
                }
            } catch (WebsocketDisconnectedException e) {
                log.warn("User stream disconnected, reconnecting in 1s");
                try {
                    sleep(1000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                log.error("Unexpected error in user stream, reconnecting in 5s", e);
                try {
                    sleep(5000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
            } finally {
                wsConnection.disconnect();
                wsConnection = null;
            }
        }
    }

    private void processMessage(String message) {
        JsonNode eventMessage = objectMapper.readTree(message);
        lastRecvTime = Instant.now();
        String eventType = eventMessage.get("e").asString();
        if(eventType.equals("ORDER_TRADE_UPDATE")) {
            JsonNode orderMessage = eventMessage.get("o");
            String clientOrderId = orderMessage.get("c").asString();
            String exchangeOrderId = orderMessage.get("i").asString();

            String tradingPair = tradingPairSymbolRegistry.convertExchangeSymbolToTradingPair(orderMessage.get("s").asString());
            String tradeId = orderMessage.get("t").asString();
            if(!tradeId.equals("0")) { // 0이 아니라는 것은 trade가 발생했음을 의미
                BigDecimal fillPrice = orderMessage.get("L").asDecimal();
                BigDecimal fillBaseAmount = orderMessage.get("l").asDecimal();
                BigDecimal fillQuoteAmount = fillPrice.multiply(fillBaseAmount);
                String feeToken = orderMessage.get("N").asString();
                BigDecimal feeAmount = orderMessage.get("n").asDecimal();
                TokenAmount fee = feeToken.equals("0") ? null : new TokenAmount(feeToken, feeAmount);
                TradeUpdateEvent tradeUpdate = new TradeUpdateEvent(
                        tradeId,
                        clientOrderId,
                        exchangeOrderId,
                        tradingPair,
                        Instant.ofEpochMilli(orderMessage.get("T").asLong()),
                        fillPrice,
                        fillBaseAmount,
                        fillQuoteAmount,
                        fee,
                        orderMessage.get("m").asBoolean()
                );
                exchangeEventPublisher.publish(tradeUpdate);
            }

            OrderUpdateEvent orderUpdate = new OrderUpdateEvent(
                    tradingPair,
                    Instant.ofEpochMilli(eventMessage.get("T").asLong()),
                    BinanceDerivativeApiSpec.ORDER_STATE.get(orderMessage.get("X").asString()),
                    clientOrderId,
                    exchangeOrderId
            );
            exchangeEventPublisher.publish(orderUpdate);
        }
        if(eventType.equals("ACCOUNT_UPDATE")) {
            Instant timestamp = Instant.ofEpochMilli(eventMessage.get("T").asLong());
            JsonNode updateData = eventMessage.get("a");
            // balance update
            for (JsonNode asset: updateData.path("B")) {
                String assetName = asset.get("a").asString();
                BigDecimal totalBalance = asset.get("wb").asDecimal();
                BigDecimal availableBalance = asset.get("cw").asDecimal();
                new BalanceUpdateEvent(assetName, totalBalance, availableBalance, timestamp);
            }
            // position update
            for (JsonNode asset : updateData.path("P")) {
                String exchangeSymbol = asset.get("s").asString();
                String tradingPair = tradingPairSymbolRegistry.convertExchangeSymbolToTradingPair(exchangeSymbol);
                PositionSide positionSide = PositionSide.valueOf(asset.get("ps").asString());
                exchangeEventPublisher.publish(new PositionUpdateEvent(
                        tradingPair,
                        positionSide,
                        asset.get("pa").asDecimal(),
                        asset.get("ep").asDecimal(),
                        asset.get("up").asDecimal(),
                        timestamp
                ));
            }
        }
    }

    @Override
    public void start() {
        connectionFuture = ioExecutor.submit(this::connectionLoop);
        scheduledFuture = taskScheduler.scheduleWithFixedDelay(
                this::renewListenKey,
                Instant.now().plus(LISTEN_KEY_KEEP_ALIVE_INTERVAL),
                LISTEN_KEY_KEEP_ALIVE_INTERVAL
        );
        running = true;
    }

    @Override
    public void stop() {
        if(scheduledFuture != null && !scheduledFuture.isDone()) {
            scheduledFuture.cancel(true);
        }
        connectionFuture.cancel(true);
        connectionFuture = null;
        scheduledFuture = null;
        running = false;
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public boolean isConnected() {
        return wsConnection != null && wsConnection.isConnected();
    }

    @Override
    public Instant getLastRecvTime() {
        return lastRecvTime;
    }
}
