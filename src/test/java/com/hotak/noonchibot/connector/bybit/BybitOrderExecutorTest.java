package com.hotak.noonchibot.connector.bybit;

import com.hotak.noonchibot.connector.*;
import com.hotak.noonchibot.connector.web.RestAssistant;
import com.hotak.noonchibot.connector.web.TimeSynchronizer;
import com.hotak.noonchibot.core.event.ExchangeEventPublisher;
import com.hotak.noonchibot.core.order.OrderState;
import com.hotak.noonchibot.core.order.OrderTracker;
import com.hotak.noonchibot.core.order.execute.AbstractExchangeOrderExecutorTest;
import com.hotak.noonchibot.core.order.execute.AbstractExchangeOrderExecutor;
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

public class BybitOrderExecutorTest extends AbstractExchangeOrderExecutorTest {
    public BybitOrderExecutorTest() {
        super(BybitApiSpec.ORDER_REALTIME_PATH_URL, BybitApiSpec.ACCOUNTS_PATH_URL, BybitApiSpec.MY_TRADES_PATH_URL);
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
        return new BybitOrderExecutor(
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
                "{\"retCode\":" + BybitApiSpec.ORDER_NOT_EXIST_ERROR_CODE + "}"
        );
    }

    protected JsonNode createOrderStatusResponse(String exchangeOrderId, OrderState state) {
        String bybitStatus = BybitApiSpec.ORDER_STATE.entrySet().stream()
                .filter(e -> e.getValue() == state)
                .map(Map.Entry::getKey)
                .findFirst()
                .orElseThrow();
        ObjectNode result = objectMapper.createObjectNode();
        ArrayNode list = result.putObject("result").putArray("list");
        ObjectNode order = list.addObject();
        order.put("orderId", exchangeOrderId);
        order.put("orderStatus", bybitStatus);
        order.put("updatedTime", Instant.now().toEpochMilli());
        return result;
    }

    protected JsonNode createAccountBalanceResponse(Map<String, BigDecimal> lockedAsset, Map<String, BigDecimal> freeAsset) {
        ObjectNode accountInfo = objectMapper.createObjectNode();
        accountInfo.put("time", Instant.now().toEpochMilli());
        ArrayNode coins = accountInfo.putObject("result").putArray("list").addObject().putArray("coin");
        Set<String> allAssets = new HashSet<>();
        allAssets.addAll(freeAsset.keySet());
        allAssets.addAll(lockedAsset.keySet());
        for (String asset : allAssets) {
            BigDecimal free = freeAsset.getOrDefault(asset, BigDecimal.ZERO);
            BigDecimal locked = lockedAsset.getOrDefault(asset, BigDecimal.ZERO);
            ObjectNode entry = coins.addObject();
            entry.put("coin", asset);
            entry.put("walletBalance", free.add(locked).toPlainString());
            entry.put("availableToWithdraw", free.toPlainString());
        }
        return accountInfo;
    }

    @Override
    protected JsonNode createOrderPlacementResponse(String exchangeOrderId) {
        ObjectNode result = objectMapper.createObjectNode();
        result.put("retCode", 0);
        result.putObject("result").put("orderId", exchangeOrderId);
        return result;
    }

    @Override
    protected JsonNode createCancelResponse(String exchangeOrderId) {
        ObjectNode result = objectMapper.createObjectNode();
        result.put("retCode", 0);
        result.putObject("result").put("orderId", exchangeOrderId);
        return result;
    }
}
