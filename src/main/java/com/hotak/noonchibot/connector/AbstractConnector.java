package com.hotak.noonchibot.connector;

import com.hotak.noonchibot.core.TimeIterator;
import com.hotak.noonchibot.core.datatype.InFlightOrder;
import com.hotak.noonchibot.core.datatype.OrderType;
import com.hotak.noonchibot.core.datatype.PositionAction;
import com.hotak.noonchibot.core.datatype.TradeType;
import com.hotak.noonchibot.core.event.EventLogger;

import java.time.Instant;
import java.util.Map;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public abstract class AbstractConnector extends TimeIterator implements Connector  {
    private final ConcurrentMap<String, BigDecimal> accountBalances = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, BigDecimal> accountAvailableBalances = new ConcurrentHashMap<>();
    private final AtomicBoolean realTimeBalanceUpdate = new AtomicBoolean(true);
    private final ConcurrentMap<String, InFlightOrder> inFlightOrdersSnapshot = new ConcurrentHashMap<>();
    private final AtomicReference<Instant> inFlightOrdersSnapshotTimestamp = new AtomicReference<>(Instant.EPOCH);
    private final ConcurrentMap<String, ConcurrentMap<String, BigDecimal>> balanceAssetLimits = new ConcurrentHashMap<>();
    private final EventLogger eventLogger = new EventLogger(name);

    public Map<String, BigDecimal> getAllBalances() {
        return new HashMap<>(accountBalances);
    }

    public BigDecimal getBalance(String currency) {
        return accountBalances.get(currency);
    }

    public abstract Map<String, InFlightOrder> getInFlightOrders();

    public BigDecimal getAvailableBalance(String currency) {
        BigDecimal availableBalance = accountAvailableBalances.get(currency);
        Map<String, BigDecimal> balanceLimits = getBalanceLimit(name);
        if(balanceLimits.containsKey(currency)) {
            availableBalance = applyBalanceLimit(currency, availableBalance, balanceLimits.get(currency));
        }
        return availableBalance;
    }

    public BigDecimal applyBalanceLimit(String currency, BigDecimal availableBalance, BigDecimal limit) {
        BigDecimal inFlightBalance = getInFlightAssetBalances(getInFlightOrders()).getOrDefault(currency, BigDecimal.ZERO);
        limit = limit.subtract(inFlightBalance);
        BigDecimal filledBalance = getOrderFilledBalances().getOrDefault(currency, BigDecimal.ZERO);
        limit = limit.add(filledBalance);
        limit = limit.max(BigDecimal.ZERO);
        return availableBalance.min(limit);
    }

    public Map<String, BigDecimal> getInFlightAssetBalances(Map<String, InFlightOrder> inFlightOrders) {
        Map<String, BigDecimal> assetBalances = new HashMap<>();

        if (inFlightOrders == null) {
            return assetBalances;
        }

        for (InFlightOrder order : inFlightOrders.values()) {
            if (order.isDone()) continue;
            BigDecimal outstandingAmount = order.getAmount().subtract(order.getExecutedAmountBase());

            if (order.getTradeType() == TradeType.BUY) {
                BigDecimal outstandingValue = outstandingAmount.multiply(order.getPrice());
                BigDecimal feePct = estimateTradeFee(TradeType.BUY).percent;
                outstandingValue = outstandingValue.multiply(BigDecimal.ONE.add(feePct));
                assetBalances.merge(order.getQuoteAsset(), outstandingValue, BigDecimal::add);
            } else {
                assetBalances.merge(order.getBaseAsset(), outstandingAmount, BigDecimal::add);
            }
        }
        return assetBalances;
    }

    private Map<String, BigDecimal> getBalanceLimit(String market) {
        return balanceLimit.getOrDefault(market, new HashMap<>());
    }

    private TradeFee estimateTradeFee(TradeType tradeType) {
        return feeEstimator.buildTradeFee(name, true, tradeType);
    }

    public Map<String, BigDecimal> getOrderFilledBalances(Instant startTimestamp) {
        Stream<OrderFilledEvent> filledEventsStream = eventLogger.getEventLog().stream()
                .filter(e -> e instanceof OrderFilledEvent)
                .map(e -> (OrderFilledEvent) e);
        if(startTimestamp != null) {
            filledEventsStream = filledEventsStream.filter(e -> e.timestamp().isAfter(startTimestamp));
        }
        List<OrderFilledEvent> filledEvents = filledEventsStream.toList();

        Map<String, BigDecimal> deltaBalances = new HashMap<>();
        for (OrderFilledEvent event : filledEvents) {
            // "BTC-USDT" -> ["BTC", "USDT"]
            String[] assets = event.pair().split("-");
            String baseAsset = assets[0];
            String quoteAsset = assets[1];
            BigDecimal price = event.price();
            BigDecimal amount = event.amount();
            BigDecimal baseDelta;
            BigDecimal quoteDelta;
            if (event.tradeTyp3() == TradeType.BUY) {
                baseDelta = amount;
                quoteDelta = price.multiply(amount).negate();
            } else {
                baseDelta = amount.negate();
                quoteDelta = price.multiply(amount);
            }

            deltaBalances.merge(baseAsset, baseDelta, BigDecimal::add);
            deltaBalances.merge(quoteAsset, quoteDelta, BigDecimal::add);
        }
        return deltaBalances;
    }

    public Map<String, BigDecimal> getOrderFilledBalances() {
        return getOrderFilledBalances(null);
    }

    @Override
    public abstract String buy(String tradingPair, BigDecimal amount, OrderType orderType, BigDecimal price, Instant expirationTs, PositionAction positionAction);

    @Override
    public abstract String sell(String tradingPair, BigDecimal amount, OrderType orderType, BigDecimal price, Instant expirationTs, PositionAction positionAction);

    @Override
    public abstract void cancel(String tradingPair, String orderId);

    @Override
    public abstract void stopTrackingOrder(String orderId);

    @Override
    public abstract BigDecimal getPrice(String tradingPair, boolean isBuy, BigDecimal amount);

    @Override
    public abstract BigDecimal getOrderPriceQuantum(String tradingPair, BigDecimal price);

    @Override
    public abstract BigDecimal getOrderSizeQuantum(String tradingPair, BigDecimal size);
}

