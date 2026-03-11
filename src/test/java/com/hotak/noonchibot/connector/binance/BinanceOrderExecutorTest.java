package com.hotak.noonchibot.connector.binance;

import com.hotak.noonchibot.connector.*;
import com.hotak.noonchibot.connector.web.RestAssistant;
import com.hotak.noonchibot.connector.web.TimeSynchronizer;
import com.hotak.noonchibot.core.event.ExchangeEventPublisher;
import com.hotak.noonchibot.core.order.InFlightOrder;
import com.hotak.noonchibot.core.order.OrderTracker;
import com.hotak.noonchibot.core.order.executor.AbstractExchangeOrderExecutor;
import com.hotak.noonchibot.core.order.executor.AbstractExchangeOrderExecutorTest;
import com.hotak.noonchibot.core.orderbook.OrderBookDataSource;
import org.springframework.http.HttpStatusCode;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class BinanceOrderExecutorTest extends AbstractExchangeOrderExecutorTest {
    public BinanceOrderExecutorTest() {
        super(BinanceApiSpec.ORDER_PATH_URL, BinanceApiSpec.ACCOUNTS_PATH_URL, BinanceApiSpec.MY_TRADES_PATH_URL);
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
            OrderBookDataSource orderBookDataSource,
            ExchangeEventPublisher exchangeEventPublisher,
            TimeSynchronizer timeSynchronizer
    ) {
        return new BinanceOrderExecutor(
                orderIdGenerator,
                orderTracker,
                tradingRuleRegistry,
                tradingPairSymbolRegistry,
                orderBookDataSource,
                timeSynchronizer,
                exchangeEventPublisher,
                restAssistant
        );
    }

    @Override
    protected Exception createOrderNotFoundException() {
        return new ExchangeApiException(
                HttpStatusCode.valueOf(400),
                "{\"code\":" + BinanceApiSpec.UNKNOWN_ORDER_ERROR_CODE + ",\"msg\":\"" + BinanceApiSpec.UNKNOWN_ORDER_MESSAGE + "\"}"
        );
    }

    protected JsonNode createOrderStatusResponse(String exchangeOrderId, InFlightOrder.State state) {
        String binanceStatus = BinanceApiSpec.ORDER_STATE.entrySet().stream()
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
