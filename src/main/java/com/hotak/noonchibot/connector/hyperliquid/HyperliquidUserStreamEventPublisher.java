package com.hotak.noonchibot.connector.hyperliquid;

import com.hotak.noonchibot.connector.TradingPairSymbolRegistry;
import com.hotak.noonchibot.connector.web.*;
import com.hotak.noonchibot.core.AbstractWebsocketDataSource;
import com.hotak.noonchibot.core.IoExecutor;
import com.hotak.noonchibot.core.order.OrderUpdateDto;
import com.hotak.noonchibot.core.event.ExchangeEventPublisher;
import com.hotak.noonchibot.core.event.FundingPaymentEvent;
import com.hotak.noonchibot.core.order.OrderState;

import com.hotak.noonchibot.core.trade.TokenAmount;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Slf4j
class HyperliquidUserStreamEventPublisher extends AbstractWebsocketDataSource {
    private final List<WsChannelHandler> handlers;

    public HyperliquidUserStreamEventPublisher(
            WsAssistantImpl wsAssistant,
            ObjectMapper objectMapper,
            IoExecutor ioExecutor,
            String userAddress,
            TradingPairSymbolRegistry tradingPairSymbolRegistry,
            ExchangeEventPublisher exchangeEventPublisher
    ) {
        super(wsAssistant, DerivativeApiSpec.WS_URL, objectMapper, ioExecutor);
        handlers = List.of(
                new OrderUpdatesHandler(userAddress, tradingPairSymbolRegistry, exchangeEventPublisher),
                new UserEventHandler(userAddress, tradingPairSymbolRegistry, exchangeEventPublisher)
        );
    }


    @Override
    protected void onConnected() {
        subscribeAll();
    }

    @Override
    protected void processMessage(WsResponse wsResponse) {
        JsonNode root = objectMapper.readTree(wsResponse.data());
        String channel = root.path("channel").asString();
        if ("subscriptionResponse".equals(channel) || "pong".equals(channel)) {
            return;
        }
        WsChannelHandler matched = handlers.stream()
                .filter(handler -> handler.channel().equals(channel))
                .findAny()
                .orElse(null);
        if (matched == null) {
            log.debug("No handler for channel: {}", channel);
            return;
        }
        matched.handle(root.path("data"));
    }

    private void subscribeAll() {
        for (WsChannelHandler handler : handlers) {
            Map<String, Object> msg = Map.of(
                    "method", "subscribe",
                    "subscription", handler.subscriptionPayload()
            );
            wsConnection.send(new WsRequest(objectMapper.writeValueAsString(msg), false));
            log.info("Subscribed to channel: {}", handler.channel());
        }
    }

    private interface WsChannelHandler {
        String channel();

        /**
         * 구독 시 보낼 subscription 객체.
         * 메시지 전체가 아니라 "subscription" 필드 안에 들어갈 본문만 반환.
         */
        Map<String, Object> subscriptionPayload();

        /**
         * 해당 채널 메시지 도착 시 호출. data는 채널 페이로드 (메시지의 "data" 필드)
         */
        void handle(JsonNode data);
    }

    @Slf4j
    @RequiredArgsConstructor
    public class OrderUpdatesHandler implements WsChannelHandler {
        private final String userAddress;
        private final TradingPairSymbolRegistry tradingPairSymbolRegistry;
        private final ExchangeEventPublisher exchangeEventPublisher;

        @Override
        public String channel() {
            return "orderUpdates";
        }

        @Override
        public Map<String, Object> subscriptionPayload() {
            return Map.of("type", "orderUpdates", "user", userAddress);
        }

        @Override
        public void handle(JsonNode data) {
            for (JsonNode entry : data) {
                JsonNode order = entry.get("order");
                String status = entry.get("status").asString();

                OrderState state = DerivativeApiSpec.ORDER_STATE.get(status);
                if (state == null) {
                    log.debug("Unmapped order status: {}", status);
                    continue;
                }
                long statusTs = entry.path("statusTimestamp").asLong();
                String coin = order.get("coin").asString();
                String tradingPair = tradingPairSymbolRegistry.convertExchangeSymbolToTradingPair(coin);
                String exchangeOrderId = String.valueOf(order.get("oid").asLong());
                JsonNode cloidNode = order.path("cloid");
                if(cloidNode.isMissingNode() || cloidNode.isNull()) {
                    // 강제 청산 등 시스템에서 자동적으로 발생한 오더 -> 추후 trade 에서
                    continue;
                }
                String clientOrderId = order.get("cloid").();

                exchangeEventPublisher.publish(new OrderUpdateDto(
                        tradingPair,
                        Instant.ofEpochMilli(statusTs),
                        state,
                        clientOrderId,
                        exchangeOrderId
                ));
            }
        }
    }

    @Slf4j
    @RequiredArgsConstructor
    public class UserEventHandler implements WsChannelHandler {
        private final String userAddress;
        private final TradingPairSymbolRegistry tradingPairSymbolRegistry;
        private final ExchangeEventPublisher exchangeEventPublisher;

        @Override
        public String channel() {
            return "user";
        }

        @Override
        public Map<String, Object> subscriptionPayload() {
            return Map.of("type", "userEvents", "user", userAddress);
        }

        @Override
        public void handle(JsonNode data) {
            if(data.has("fills")) {
                handleFills(data);
                return;
            }
            if(data.has("funding")) {
                handleFunding(data);
            }
        }


        private void handleFills(JsonNode data) {
            for (JsonNode fill : data.path("fills")) {
                BigDecimal price = fill.get("px").decimalValue();
                BigDecimal size = fill.get("sz").decimalValue();

                String coin = fill.get("coin").asString();
                String tradingPair = tradingPairSymbolRegistry.convertExchangeSymbolToTradingPair(coin);

                String tradeId = String.valueOf(fill.get("tid").asLong());
                String exchangeOrderId = String.valueOf(fill.get("oid").asLong());
                // crossed=true → taker
                boolean isMaker = !fill.get("crossed").asBoolean();

                TokenAmount fee = new TokenAmount(
                        fill.get("feeToken").asString(),
                        fill.get("fee").decimalValue()
                );
                exchangeEventPublisher.publish(new TradeUpdateEvent(
                        tradeId,
                        null,
                        exchangeOrderId,
                        tradingPair,
                        Instant.ofEpochMilli(fill.get("time").asLong()),
                        price,
                        size,
                        price.multiply(size),
                        fee,
                        isMaker
                ));
            }
        }

        private void handleFunding(JsonNode data) {
            JsonNode funding = data.path("funding");

            String coin = funding.get("coin").asString();
            String tradingPair = tradingPairSymbolRegistry.convertExchangeSymbolToTradingPair(coin);

            Instant settledAt = Instant.ofEpochMilli(funding.get("time").asLong());
            BigDecimal amount = funding.get("usdc").asDecimal();
            BigDecimal fundingRate = funding.get("fundingRate").asDecimal();
            BigDecimal positionSize = funding.get("szi").asDecimal();

            exchangeEventPublisher.publish(new FundingPaymentEvent(
                    tradingPair,
                    settledAt,
                    amount,
                    fundingRate,
                    positionSize
            ));
        }

        private String processClientId() {

        }
    }
}