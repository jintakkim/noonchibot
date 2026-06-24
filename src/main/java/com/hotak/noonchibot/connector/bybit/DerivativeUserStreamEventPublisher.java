package com.hotak.noonchibot.connector.bybit;

import com.hotak.noonchibot.connector.LifecycleComponent;
import com.hotak.noonchibot.connector.TradingPairSymbolRegistry;
import com.hotak.noonchibot.connector.web.*;
import com.hotak.noonchibot.core.IoExecutor;
import com.hotak.noonchibot.core.order.OrderUpdateDto;
import com.hotak.noonchibot.core.datatype.WebsocketStatus;
import com.hotak.noonchibot.core.derivative.PositionSide;
import com.hotak.noonchibot.core.event.BalanceUpdateEvent;
import com.hotak.noonchibot.core.event.ExchangeEventPublisher;
import com.hotak.noonchibot.core.event.PositionUpdateEvent;
import com.hotak.noonchibot.core.trade.TokenAmount;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledFuture;

import static java.lang.Thread.sleep;

@Slf4j
@RequiredArgsConstructor
class DerivativeUserStreamEventPublisher implements LifecycleComponent, WebsocketStatus {
    private final WsAssistantImpl wsAssistant;
    private final ObjectMapper objectMapper;
    private final TradingPairSymbolRegistry tradingPairSymbolRegistry;
    private final ExchangeEventPublisher exchangeEventPublisher;
    private final IoExecutor ioExecutor;

    private volatile ScheduledFuture<?> scheduledFuture;
    private volatile WsConnectionImpl wsConnection;
    private volatile Future<?> connectionFuture;
    private volatile Instant lastRecvTime;

    private void connectionLoop() {
        while (!Thread.currentThread().isInterrupted()) {
            try {

                this.wsConnection = wsAssistant.connect(URI.create(DerivativeApiSpec.WSS_API_URL));
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
                if (wsConnection != null) {
                    wsConnection.disconnect();
                    wsConnection = null;
                }
            }
        }
    }

    private void processMessage(String message) {
        JsonNode eventMessage = objectMapper.readTree(message);
        lastRecvTime = Instant.now();

        if (!eventMessage.has("topic")) {
            return;
        }

        String topic = eventMessage.get("topic").asString();
        JsonNode dataArray = eventMessage.get("data");

        if (dataArray == null || !dataArray.isArray()) {
            return;
        }

        switch (topic) {
            case "execution" -> {
                for (JsonNode exec : dataArray) {
                    if (!"Trade".equals(exec.path("execType").asString())) {
                        continue;
                    }

                    String tradeId = exec.get("execId").asString();
                    String clientOrderId = exec.path("orderLinkId").asString();
                    String exchangeOrderId = exec.get("orderId").asString();
                    String exchangeSymbol = exec.get("symbol").asString();
                    String tradingPair = tradingPairSymbolRegistry.convertExchangeSymbolToTradingPair(exchangeSymbol);
                    BigDecimal fillPrice = exec.get("execPrice").asDecimal();
                    BigDecimal fillBaseAmount = exec.get("execQty").asDecimal();
                    BigDecimal fillQuoteAmount = exec.get("execValue").asDecimal();
                    String feeCurrency = exec.path("feeCurrency").asString();
                    BigDecimal execFee = exec.path("execFee").asDecimal();

                    TokenAmount fee = null;
                    if (!feeCurrency.isBlank()) {
                        fee = new TokenAmount(feeCurrency, execFee);
                    }

                    boolean isMaker = exec.get("isMaker").asBoolean();

                    TradeUpdateEvent tradeUpdate = new TradeUpdateEvent(
                            tradeId,
                            clientOrderId,
                            exchangeOrderId,
                            tradingPair,
                            Instant.ofEpochMilli(exec.get("execTime").asLong()),
                            fillPrice,
                            fillBaseAmount,
                            fillQuoteAmount,
                            fee,
                            isMaker
                    );
                    exchangeEventPublisher.publish(tradeUpdate);
                }
            }
            case "order" -> {
                for (JsonNode order : dataArray) {
                    String exchangeSymbol = order.get("symbol").asString();
                    String tradingPair = tradingPairSymbolRegistry.convertExchangeSymbolToTradingPair(exchangeSymbol);
                    String clientOrderId = order.path("orderLinkId").asString();
                    String exchangeOrderId = order.get("orderId").asString();

                    OrderUpdateDto orderUpdate = new OrderUpdateDto(
                            tradingPair,
                            Instant.ofEpochMilli(order.get("updatedTime").asLong()),
                            DerivativeApiSpec.ORDER_STATE.get(order.get("orderStatus").asString()),
                            clientOrderId,
                            exchangeOrderId
                    );
                    exchangeEventPublisher.publish(orderUpdate);
                }
            }
            case "wallet" -> {
                Instant timestamp = Instant.ofEpochMilli(eventMessage.get("creationTime").asLong());

                for (JsonNode wallet : dataArray) {
                    JsonNode coinArray = wallet.get("coin");
                    if (coinArray == null || !coinArray.isArray()) continue;

                    for (JsonNode coinNode : coinArray) {
                        // availableToWithdraw 는 deprecated.
                        // isolated margin: walletBalance - locked - totalOrderIM - totalPositionIM - bonus
                        BigDecimal walletBalance = coinNode.get("walletBalance").asDecimal();
                        BigDecimal locked = coinNode.get("locked").asDecimal();
                        BigDecimal orderIM = coinNode.get("totalOrderIM").asDecimal();
                        BigDecimal positionIM = coinNode.get("totalPositionIM").asDecimal();
                        BigDecimal bonus = coinNode.get("bonus").asDecimal();
                        BigDecimal available = walletBalance
                                .subtract(locked)
                                .subtract(orderIM)
                                .subtract(positionIM)
                                .subtract(bonus);

                        BalanceUpdateEvent balanceUpdate = new BalanceUpdateEvent(
                                coinNode.get("coin").asString(),
                                walletBalance,
                                available,
                                timestamp
                        );

                        exchangeEventPublisher.publish(balanceUpdate);
                    }
                }
            }
            case "position" -> {
                for (JsonNode pos : dataArray) {
                    String exchangeSymbol = pos.path("symbol").asString();
                    String tradingPair = tradingPairSymbolRegistry.convertExchangeSymbolToTradingPair(exchangeSymbol);

                    PositionUpdateEvent posUpdate = new PositionUpdateEvent(
                            tradingPair,
                            PositionSide.valueOf(pos.path("side").asString().toUpperCase()),
                            pos.path("size").asDecimal(),
                            pos.path("entryPrice").asDecimal(),
                            pos.path("unrealisedPnl").asDecimal(),
                            Instant.ofEpochMilli(pos.path("updatedTime").asLong())
                    );

                    exchangeEventPublisher.publish(posUpdate);
                }
            }
        }
    }

    @Override
    public void start() {
        connectionFuture = ioExecutor.submit(this::connectionLoop);
    }

    @Override
    public void shutdown() {
        if(scheduledFuture != null && !scheduledFuture.isDone()) {
            scheduledFuture.cancel(true);
        }
        connectionFuture.cancel(true);
        connectionFuture = null;
        scheduledFuture = null;
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