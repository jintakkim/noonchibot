package com.hotak.noonchibot.connector.binance.derivative;

import com.hotak.noonchibot.connector.TradingPairSymbolRegistry;
import com.hotak.noonchibot.connector.binance.BinanceExchangeErrorClassifier;
import com.hotak.noonchibot.connector.web.*;
import com.hotak.noonchibot.core.AbstractWebsocketDataSource;
import com.hotak.noonchibot.core.Exchange;
import com.hotak.noonchibot.core.IoExecutor;
import com.hotak.noonchibot.core.LifecycleAware;
import com.hotak.noonchibot.core.config.Phases;
import com.hotak.noonchibot.core.derivative.FundingPayment;
import com.hotak.noonchibot.core.derivative.PositionSide;
import com.hotak.noonchibot.core.event.EventPublisher;
import com.hotak.noonchibot.core.event.internal.derivative.FundingPaymentEvent;
import com.hotak.noonchibot.core.event.internal.derivative.PositionEvent;
import com.hotak.noonchibot.core.event.internal.order.OrderEvent;
import com.hotak.noonchibot.core.event.internal.trade.TradeEvent;
import com.hotak.noonchibot.core.resilience.CircuitBreakerNames;
import com.hotak.noonchibot.core.trade.TokenAmount;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpMethod;
import org.springframework.scheduling.TaskScheduler;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledFuture;

@Slf4j
class UserStreamDataSource extends AbstractWebsocketDataSource implements LifecycleAware {
    private static final Duration LISTEN_KEY_KEEP_ALIVE_INTERVAL = Duration.ofMinutes(45);

    private final RestAssistant restAssistant;
    private final TaskScheduler taskScheduler;
    private final TradingPairSymbolRegistry tradingPairSymbolRegistry;
    private final EventPublisher eventPublisher;

    private volatile ScheduledFuture<?> listenKeyKeepAliveTask;

    public UserStreamDataSource(
            RestAssistant restAssistant,
            WsAssistant wsAssistant,
            TaskScheduler taskScheduler,
            ObjectMapper objectMapper,
            TradingPairSymbolRegistry tradingPairSymbolRegistry,
            EventPublisher eventPublisher,
            IoExecutor ioExecutor
    ) {
        super(wsAssistant, objectMapper, ioExecutor);
        this.restAssistant = restAssistant;
        this.taskScheduler = taskScheduler;
        this.tradingPairSymbolRegistry = tradingPairSymbolRegistry;
        this.eventPublisher = eventPublisher;
    }

    public UserStreamDataSource(
            RestAssistant restAssistant,
            WsAssistant wsAssistant,
            TaskScheduler taskScheduler,
            ObjectMapper objectMapper,
            TradingPairSymbolRegistry tradingPairSymbolRegistry,
            EventPublisher eventPublisher,
            IoExecutor ioExecutor,
            CircuitBreakerRegistry circuitBreakerRegistry
    ) {
        this(
                new RestAssistantConfigurer(restAssistant)
                        .circuit(circuitBreakerRegistry, CircuitBreakerNames.userStream(Exchange.BINANCE_DERIVATIVE))
                        .errorClassifier(new BinanceExchangeErrorClassifier())
                        .maxRetry(2)
                        .build(),
                wsAssistant,
                taskScheduler,
                objectMapper,
                tradingPairSymbolRegistry,
                eventPublisher,
                ioExecutor
        );
    }


    @Override
    protected URI connectionUri() {
        return URI.create(ApiSpec.WSS_PRIVATE_URL + "/" + fetchListenKey());
    }

    @Override
    protected void onConnected() {
        // user stream은 subscribe message 없음
    }

    @Override
    protected void processMessage(WsResponse response) {
        if (response.messageType() != WsResponse.MessageType.TEXT) {
            throw new IllegalStateException("cant handle non-text message");
        }

        JsonNode eventMessage = objectMapper.readTree(response.data());
        String eventType = eventMessage.get("e").asString();

        switch (eventType) {
            case "ORDER_TRADE_UPDATE" -> processOrderTradeUpdate(eventMessage);
            case "ACCOUNT_UPDATE" -> processAccountUpdate(eventMessage);
            default -> log.debug("unknown user stream event: {}", eventMessage);
        }
    }

    private void processOrderTradeUpdate(JsonNode eventMessage) {
        JsonNode orderMessage = eventMessage.get("o");

        String clientOrderId = orderMessage.get("c").asString();
        String exchangeOrderId = orderMessage.get("i").asString();
        String tradingPair = tradingPairSymbolRegistry
                .convertExchangeSymbolToTradingPair(orderMessage.get("s").asString());

        publishTradeIfFilled(orderMessage, clientOrderId, exchangeOrderId, tradingPair);

        eventPublisher.publish(new OrderEvent.StatusReceived(
                tradingPair,
                clientOrderId,
                exchangeOrderId,
                ApiSpec.ORDER_STATE.get(orderMessage.get("X").asString()),
                Instant.ofEpochMilli(eventMessage.get("T").asLong())
        ));
    }

    private void publishTradeIfFilled(
            JsonNode orderMessage,
            String clientOrderId,
            String exchangeOrderId,
            String tradingPair
    ) {
        String tradeId = orderMessage.get("t").asString();
        if ("0".equals(tradeId)) return;

        BigDecimal fillPrice = orderMessage.get("L").asDecimal();
        BigDecimal fillBaseAmount = orderMessage.get("l").asDecimal();
        BigDecimal fillQuoteAmount = fillPrice.multiply(fillBaseAmount);

        String feeToken = orderMessage.path("N").asString();
        BigDecimal feeAmount = orderMessage.path("n").asDecimal();
        TokenAmount fee = feeToken == null || feeToken.isBlank() || "0".equals(feeToken)
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

    private void processAccountUpdate(JsonNode eventMessage) {
        Instant timestamp = Instant.ofEpochMilli(eventMessage.get("T").asLong());
        JsonNode updateData = eventMessage.get("a");
        if ("FUNDING_FEE".equals(updateData.path("m").asString())) {
            publishFundingPayments(updateData, timestamp);
        }

        for (JsonNode position : updateData.path("P")) {
            String tradingPair = tradingPairSymbolRegistry
                    .convertExchangeSymbolToTradingPair(position.get("s").asString());

            eventPublisher.publish(new PositionEvent.UpdateReceived(
                    tradingPair,
                    PositionSide.valueOf(position.get("ps").asString()),
                    position.get("pa").asDecimal(),
                    position.get("ep").asDecimal(),
                    position.get("up").asDecimal(),
                    timestamp
            ));
        }
    }

    private void publishFundingPayments(JsonNode updateData, Instant timestamp) {
        List<FundingPayment> payments = new ArrayList<>();
        BigDecimal amount = findFundingAmount(updateData);
        if (amount.compareTo(BigDecimal.ZERO) == 0) return;
        String asset = findFundingAsset(updateData);

        for (JsonNode position : updateData.path("P")) {
            String tradingPair = tradingPairSymbolRegistry
                    .convertExchangeSymbolToTradingPair(position.get("s").asString());
            PositionSide positionSide = PositionSide.valueOf(position.get("ps").asString());
            payments.add(new FundingPayment(
                    createFundingPaymentId(tradingPair, positionSide, asset, amount, timestamp),
                    Exchange.BINANCE_DERIVATIVE,
                    tradingPair,
                    positionSide,
                    amount,
                    asset,
                    timestamp
            ));
        }
        payments.forEach(payment ->
                eventPublisher.publish(new FundingPaymentEvent.Received(payment))
        );
    }

    private BigDecimal findFundingAmount(JsonNode updateData) {
        for (JsonNode balance : updateData.path("B")) {
            BigDecimal balanceChange = balance.path("bc").asDecimal();
            if (balanceChange.compareTo(BigDecimal.ZERO) != 0) {
                return balanceChange;
            }
        }
        return BigDecimal.ZERO;
    }

    private String findFundingAsset(JsonNode updateData) {
        for (JsonNode balance : updateData.path("B")) {
            if (balance.path("bc").asDecimal().compareTo(BigDecimal.ZERO) != 0) {
                return balance.get("a").asString();
            }
        }
        return "USDT";
    }

    private String createFundingPaymentId(
            String tradingPair,
            PositionSide positionSide,
            String asset,
            BigDecimal amount,
            Instant timestamp
    ) {
        return String.join(
                ":",
                Exchange.BINANCE_DERIVATIVE.name(),
                tradingPair,
                positionSide.name(),
                asset,
                timestamp.toString(),
                amount.toPlainString()
        );
    }

    @Override
    public void onStart() {
        super.onStart();
        listenKeyKeepAliveTask = taskScheduler.scheduleWithFixedDelay(
                this::renewListenKey,
                Instant.now().plus(LISTEN_KEY_KEEP_ALIVE_INTERVAL),
                LISTEN_KEY_KEEP_ALIVE_INTERVAL
        );
    }

    @Override
    public void onShutdown() {
        super.onShutdown();
        if (listenKeyKeepAliveTask != null) {
            listenKeyKeepAliveTask.cancel(true);
            listenKeyKeepAliveTask = null;
        }
    }

    @Override
    public int phase() {
        return Phases.USER_STREAM_DATASOURCE_SETUP;
    }

    private String fetchListenKey() {
        RestRequest request = RestRequest.builder()
                .method(HttpMethod.POST)
                .pathUrl(ApiSpec.LISTEN_KEY_PATH_URL)
                .authRequired(true)
                .build();

        return restAssistant.executeRequestAndGetJsonBody(request)
                .get("listenKey")
                .asString();
    }

    private void renewListenKey() {
        RestRequest request = RestRequest.builder()
                .method(HttpMethod.PUT)
                .pathUrl(ApiSpec.LISTEN_KEY_PATH_URL)
                .authRequired(true)
                .build();

        restAssistant.executeRequestAndGetResponse(request);
    }
}
