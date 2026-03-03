package com.hotak.noonchibot.connector.binance;

import com.hotak.noonchibot.connector.AbstractExchangeConnector;
import com.hotak.noonchibot.core.datatype.TradeType;
import com.hotak.noonchibot.core.datatype.TradingRule;
import com.hotak.noonchibot.core.order.OrderType;
import com.hotak.noonchibot.core.trade.fee.DeductedFromReturnsTradeFee;
import com.hotak.noonchibot.core.trade.fee.TradeFee;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.util.*;

import static com.hotak.noonchibot.connector.binance.BinanceApiSpec.ORDER_NOT_EXIST_ERROR_CODE;

@Slf4j
public class BinanceConnector extends AbstractExchangeConnector {
    private static final String EXCHANGE_NAME = "binance";

    public BinanceConnector() {
        super(EXCHANGE_NAME, );
    }

    @Override
    public Set<OrderType> getSupportedOrderType(String tradingPair) {
        TradingRule rule = tradingRules.get(tradingPair);
        if(rule == null) throw new IllegalArgumentException("Unknown trading pair: " + tradingPair);
        return rule.supportedOrderTypes();
    }

    @Override
    protected boolean isRequestExceptionRelatedToTimeSynchronizer(Exception e) {
        String message = e.getMessage();
        return message != null
                && message.contains(String.valueOf(BinanceApiSpec.TIMESTAMP_ERROR_CODE))
                && message.contains(BinanceApiSpec.TIMESTAMP_ERROR_MESSAGE);
    }

    @Override
    protected boolean isOrderNotFoundDuringStatusUpdateException(Exception e) {
        String message = e.getMessage();
        return message != null
                && message.contains(String.valueOf(BinanceApiSpec.ORDER_NOT_EXIST_ERROR_CODE))
                && message.contains(BinanceApiSpec.ORDER_NOT_EXIST_MESSAGE);
    }

    @Override
    protected boolean isOrderNotFoundDuringCancellationException(Exception e) {
        String message = e.getMessage();
        return message != null
                && message.contains(String.valueOf(BinanceApiSpec.UNKNOWN_ORDER_ERROR_CODE))
                && message.contains(BinanceApiSpec.UNKNOWN_ORDER_MESSAGE);
    }

    @Override
    protected List<TradingRule> parseTradingRule(JsonNode node) {
        List<TradingRule> rules = new ArrayList<>();
        JsonNode symbols = node.get("symbols");
        if (symbols == null || !symbols.isArray()) {
            return rules;
        }
        for (JsonNode symbol : symbols) {
            String exchangeSymbol = symbol.get("symbol").asString();
            String tradingPair = tradingPairSymbolRegistry.convertExchangeSymbolToTradingPair(exchangeSymbol);
            if (tradingPair == null) continue;
            JsonNode filters = symbol.get("filters");
            JsonNode priceFilter = findFilter(filters, "PRICE_FILTER");
            JsonNode lotSizeFilter = findFilter(filters, "LOT_SIZE");
            JsonNode minNotionalFilter = findFilter(filters, "NOTIONAL");
            Set<OrderType> orderTypes = new HashSet<>();
            for (JsonNode ot : symbol.get("orderTypes")) {
                try {
                    orderTypes.add(OrderType.valueOf(ot.asString()));
                } catch (IllegalArgumentException ignored) {
                }
            }
            rules.add(new TradingRule(
                    tradingPair,
                    lotSizeFilter.get("minQty").asDecimal(), //min order size
                    lotSizeFilter.get("maxQty").asDecimal(),
                    priceFilter.get("tickSize").asDecimal(),
                    lotSizeFilter.get("stepSize").asDecimal(),
                    minNotionalFilter.get("minNotional").asDecimal(),
                    symbol.get("quotePrecision").asInt(),
                    orderTypes,
                    tradingPair.split("-")[1],
                    tradingPair.split("-")[0]
            ));
        }
        return rules;
    }

    private JsonNode findFilter(JsonNode filters, String... filterTypes) {
        Set<String> types = Set.of(filterTypes);
        for (JsonNode filter : filters) {
            if (types.contains(filter.get("filterType").asString())) {
                return filter;
            }
        }
        throw new IllegalArgumentException("Filter not found: " + Arrays.toString(filterTypes));
    }

    @Override
    public TradeFee getFee(String baseCurrency, String quoteCurrency, OrderType orderType, TradeType tradeType, BigDecimal amount, BigDecimal price, Boolean isMaker) {
        boolean maker = isMaker != null ? isMaker : orderType == OrderType.LIMIT_MAKER;
        return new DeductedFromReturnsTradeFee();
    }
}
