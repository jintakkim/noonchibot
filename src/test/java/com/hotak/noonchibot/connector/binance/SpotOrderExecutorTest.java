package com.hotak.noonchibot.connector.binance;

import com.hotak.noonchibot.connector.*;
import com.hotak.noonchibot.connector.TradingRuleRegistry;
import com.hotak.noonchibot.connector.web.RestAssistant;
import com.hotak.noonchibot.connector.web.TimeSynchronizer;
import com.hotak.noonchibot.core.TestMainExecutor;
import com.hotak.noonchibot.core.event.ExchangeEventPublisher;
import com.hotak.noonchibot.core.order.OrderState;
import com.hotak.noonchibot.core.order.OrderTracker;
import com.hotak.noonchibot.core.order.execute.AbstractExchangeOrderExecutorTest;
import com.hotak.noonchibot.core.order.execute.AbstractExchangeOrderExecutor;
import com.hotak.noonchibot.core.orderbook.OrderBookTracker;
import org.springframework.http.HttpStatusCode;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

class SpotOrderExecutorTest extends AbstractExchangeOrderExecutorTest {
    public SpotOrderExecutorTest() {
        super(SpotApiSpec.ORDER_PATH_URL, SpotApiSpec.ACCOUNTS_PATH_URL, SpotApiSpec.MY_TRADES_PATH_URL);
    }

    @Override
    protected String convertTradingPairToExchangeSymbol(String tradingPair) {
        String[] symbols = tradingPair.split("-");
        return symbols[0].toUpperCase() + symbols[1].toUpperCase();
    }

    @Override
    protected AbstractExchangeOrderExecutor createExchangeOrderExecutor(
            OrderIdGenerator orderIdGenerator,
            OrderTracker orderTracker,
            TradingRuleRegistry tradingRuleRegistry,
            RestAssistant restAssistant,
            TradingPairSymbolRegistry tradingPairSymbolRegistry,
            OrderBookTracker orderBookTracker,
            ExchangeEventPublisher exchangeEventPublisher,
            TimeSynchronizer timeSynchronizer
    ) {
        return new SpotOrderExecutor(
                orderIdGenerator,
                orderTracker,
                tradingRuleRegistry,
                tradingPairSymbolRegistry,
                orderBookTracker,
                timeSynchronizer,
                exchangeEventPublisher,
                restAssistant,
                new TestMainExecutor(),
                Runnable::run
        );
    }

    @Override
    protected Exception createOrderNotFoundException() {
        return new ExchangeApiException(
                HttpStatusCode.valueOf(400),
                "{\"code\":" + SpotApiSpec.UNKNOWN_ORDER_DURING_CANCELLATION_ERROR_CODE + "}"
        );
    }

    protected JsonNode createOrderStatusResponse(String exchangeOrderId, OrderState state) {
        String binanceStatus = SpotApiSpec.ORDER_STATE.entrySet().stream()
                .filter(e -> e.getValue() == state)
                .map(Map.Entry::getKey)
                .findFirst()
                .orElseThrow();
        ObjectNode result = objectMapper.createObjectNode();
        result.put("orderId", exchangeOrderId);
        result.put("status", binanceStatus);
        result.put("updateTime", Instant.now().toEpochMilli());
        return result;
    }

    protected JsonNode createAccountBalanceResponse(Map<String, BigDecimal> lockedAsset, Map<String, BigDecimal> freeAsset) {
        ObjectNode accountInfo = objectMapper.createObjectNode();
        ArrayNode balances = accountInfo.putArray("balances");
        Set<String> allAssets = new HashSet<>();
        allAssets.addAll(freeAsset.keySet());
        allAssets.addAll(lockedAsset.keySet());
        for (String asset : allAssets) {
            ObjectNode entry = balances.addObject();
            entry.put("asset", asset);
            entry.put("free", freeAsset.getOrDefault(asset, BigDecimal.ZERO).toPlainString());
            entry.put("locked", lockedAsset.getOrDefault(asset, BigDecimal.ZERO).toPlainString());
        }
        return accountInfo;
    }

    @Override
    protected JsonNode createOrderPlacementResponse(String exchangeOrderId) {
        ObjectNode result = objectMapper.createObjectNode();
        result.put("orderId", exchangeOrderId);
        result.put("transactTime", Instant.now().toEpochMilli());
        return result;
    }

    @Override
    protected JsonNode createCancelResponse(String exchangeOrderId) {
        ObjectNode result = objectMapper.createObjectNode();
        result.put("orderId", exchangeOrderId);
        result.put("status", "CANCELED");
        return result;
    }
}
