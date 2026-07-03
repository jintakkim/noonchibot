package com.hotak.noonchibot.connector.binance.derivative;

import com.hotak.noonchibot.connector.TradingPairSymbolRegistry;
import com.hotak.noonchibot.connector.web.WsAssistant;
import com.hotak.noonchibot.connector.web.WsRequest;
import com.hotak.noonchibot.core.derivative.AbstractWsFundingInfoDataSource;
import com.hotak.noonchibot.core.event.EventPublisher;
import com.hotak.noonchibot.core.orderbook.FundingInfoMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.TaskScheduler;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

class WsFundingInfoDataSource extends AbstractWsFundingInfoDataSource {
    @RequiredArgsConstructor
    private enum MessageMethod {
        SUBSCRIBE("SUBSCRIBE"), UNSUBSCRIBE("UNSUBSCRIBE");
        private final String apiValue;
    }

    private final TradingPairSymbolRegistry tradingPairSymbolRegistry;
    private final String websocketUrl;

    public WsFundingInfoDataSource(
            WsAssistant wsAssistant,
            ObjectMapper objectMapper,
            TaskScheduler taskScheduler,
            ApplicationEventPublisher applicationEventPublisher,
            EventPublisher eventPublisher,
            TradingPairSymbolRegistry tradingPairSymbolRegistry,
            String websocketUrl
    ) {
        super(
                wsAssistant,
                objectMapper,
                taskScheduler,
                applicationEventPublisher,
                eventPublisher,
                tradingPairSymbolRegistry.getAllTradingPairs()
        );
        this.tradingPairSymbolRegistry = tradingPairSymbolRegistry;
        this.websocketUrl = websocketUrl;
    }

    @Override
    protected boolean isErrorMessage(JsonNode msg) {
        return msg.has("error");
    }

    @Override
    protected boolean isAckMessage(JsonNode msg) {
        return msg.has("id") && msg.has("result");
    }

    @Override
    protected void sendSubscribe(Set<String> tradingPairs) {
        sendFundingRateRequest(MessageMethod.SUBSCRIBE, tradingPairs);
    }

    @Override
    protected void sendUnsubscribe(Set<String> tradingPairs) {
        sendFundingRateRequest(MessageMethod.UNSUBSCRIBE, tradingPairs);
    }

    private void sendFundingRateRequest(MessageMethod method, Collection<String> tradingPairs) {
        List<String> params = tradingPairs.stream()
                .map(tradingPairSymbolRegistry::convertTradingPairToExchangeSymbol)
                .map(String::toLowerCase)
                .map(symbol -> symbol + "@markPrice")
                .toList();
        wsConnection.send(new WsRequest(Map.of(
                "method", method.apiValue,
                "params", params,
                "id", 1
        ), false));
    }

    @Override
    protected FundingInfoMessage parseFundingInfoMessage(JsonNode msg) {
        String tradingPair = tradingPairSymbolRegistry.convertExchangeSymbolToTradingPair(msg.get("s").asString());
        return new FundingInfoMessage(
                tradingPair,
                Instant.ofEpochMilli(msg.get("E").asLong()),
                msg.get("p").asDecimal(),
                msg.get("r").asDecimal(),
                Instant.ofEpochMilli(msg.get("T").asLong()),
                null
        );
    }

    @Override
    protected URI connectionUri() {
        return URI.create(websocketUrl);
    }
}
