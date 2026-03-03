package com.hotak.noonchibot.connector;

import com.hotak.noonchibot.core.PubSub;
import com.hotak.noonchibot.core.event.*;
import com.hotak.noonchibot.core.NetworkIterator;
import com.hotak.noonchibot.core.datatype.*;
import com.hotak.noonchibot.core.trade.fee.TradeFee;
import com.hotak.noonchibot.core.trade.fee.TradeFeeSchema;
import com.hotak.noonchibot.core.trade.fee.TradeFeeSchemaLoader;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.stream.Stream;

public abstract class AbstractConnector extends NetworkIterator implements Connector {
    private final String name;
    protected final Map<String, BigDecimal> accountBalances;
    protected final Map<String, BigDecimal> accountAvailableBalances;
    private final EventLogger eventLogger;
    private final TradeFeeSchemaLoader tradeFeeSchemaLoader;
    private final PubSub pubSub = new PubSub();

    private final Map<String, BigDecimal> balanceLimit;

    public AbstractConnector(String name, Map<String, BigDecimal> balanceLimit, TradeFeeSchemaLoader tradeFeeSchemaLoader) {
        this.name = name;
        this.accountBalances = new HashMap<>();
        this.accountAvailableBalances = new HashMap<>();
        this.eventLogger = new SimpleEventLogger(name);
        this.balanceLimit = balanceLimit == null ? new HashMap<>() : balanceLimit;
        this.tradeFeeSchemaLoader = tradeFeeSchemaLoader;
    }

    /**
     * 잔고 조회
     * @param currency 코인 명
     * @return 잔고
     */
    public BigDecimal getBalance(String currency) {
        return accountBalances.get(currency);
    }

    /**
     * 전체 잔고 조회
     * @return 전체 잔고(currency : balance) 쌍
     */
    public Map<String, BigDecimal> getAllBalances() {
        return new HashMap<>(accountBalances);
    }

    @Override
    public abstract Map<String, InFlightOrder> getInFlightOrders();

    @Override
    public BigDecimal getAvailableBalance(String currency) {
        BigDecimal availableBalance = accountAvailableBalances.get(currency);
        if(balanceLimit.containsKey(currency)) {
            availableBalance = applyBalanceLimit(currency, availableBalance, balanceLimit.get(currency));
        }
        return availableBalance;
    }

    private BigDecimal applyBalanceLimit(String currency, BigDecimal availableBalance, BigDecimal limit) {
        /*
         * 현재 미체결 주문(In-flight Orders)에 묶여 있는 자산 확인
         * 예: 예산 1 ETH 중 0.5 ETH가 이미 매수 주문으로 나가 있다면, 남은 예산은 0.5 ETH
         */
        BigDecimal inFlightBalance = getInFlightAssetBalances().getOrDefault(currency, BigDecimal.ZERO);
        limit = limit.subtract(inFlightBalance);
        /*
         * 봇 시작 이후 체결된 주문을 통한 자산 변동 반영
         */
        BigDecimal filledBalance = getOrderFilledBalances().getOrDefault(currency, BigDecimal.ZERO);
        limit = limit.add(filledBalance);
        /*
         * 하한선 설정 (예산은 0보다 작을 수 없음)
         */
        limit = limit.max(BigDecimal.ZERO);
        /*
         * 최종 사용 가능 금액 반환
         * (실제 거래소 잔고)와 (계산된 예산 한도) 중 더 작은 값을 사용
         */
        return availableBalance.min(limit);
    }

    @Override
    public Map<String, BigDecimal> getInFlightAssetBalances() {
        Map<String, InFlightOrder> inFlightOrders = getInFlightOrders();
        Map<String, BigDecimal> assetBalances = new HashMap<>();

        if (inFlightOrders == null) {
            return assetBalances;
        }
        // 진행 중인 주문들을 순회
        for (InFlightOrder order : inFlightOrders.values()) {
            if (order.isDone()) continue;
            // 미체결 수량 계산
            BigDecimal outstandingAmount = order.getAmount().subtract(order.getExecutedAmountBase());

            if (order.getTradeType() == TradeType.BUY) {
                // 매수인 경우: Quote 자산(예: USDT)이 잠김
                // 가치 = 미체결 수량 * 주문 가격
                BigDecimal outstandingValue = outstandingAmount.multiply(order.getPrice());
                // 수수료 계산
                BigDecimal feePct = estimateTradeFee(order.getTradingPair(), TradeType.BUY, true).getPercent();
                // outstandingValue *= (1 + feePct)
                outstandingValue = outstandingValue.multiply(BigDecimal.ONE.add(feePct));
                assetBalances.merge(order.getQuoteAsset(), outstandingValue, BigDecimal::add);
            } else {
                // 매도인 경우: Base 자산(예: BTC)이 잠김
                // Map에 Base 자산 누적
                assetBalances.merge(order.getBaseAsset(), outstandingAmount, BigDecimal::add);
            }
        }
        return assetBalances;
    }

    /**
     * 실제 발생된 수수료가 아닌 수수료 예측치
     */
    protected TradeFee estimateTradeFee(String tradingPair, TradeType tradeType, boolean isMaker) {
        TradeFeeSchema schema = tradeFeeSchemaLoader.get(tradingPair);
        return TradeFee.newSpotFee(schema, tradeType, isMaker);
    }

    @Override
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
            if (event.tradeType() == TradeType.BUY) {
                // 매수: Base 증가(+), Quote 감소(-)
                baseDelta = amount;
                quoteDelta = price.multiply(amount).negate(); // -1 * price * amount
            } else {
                // 매도: Base 감소(-), Quote 증가(+)
                baseDelta = amount.negate();
                quoteDelta = price.multiply(amount);
            }

            deltaBalances.merge(baseAsset, baseDelta, BigDecimal::add);
            deltaBalances.merge(quoteAsset, quoteDelta, BigDecimal::add);
        }
        return deltaBalances;
    }

    @Override
    public Map<String, BigDecimal> getOrderFilledBalances() {
        return getOrderFilledBalances(null);
    }

    @Override
    public abstract BigDecimal getOrderPriceQuantum(String tradingPair, BigDecimal price);

    @Override
    public abstract BigDecimal getOrderSizeQuantum(String tradingPair, BigDecimal amount);

    @Override
    public BigDecimal quantizeOrderPrice(String tradingPair, BigDecimal price) {
        if (price == null) {
            return null;
        }
        BigDecimal priceQuantum = getOrderPriceQuantum(tradingPair, price);
        // (price // quantum) * quantum
        return price.divideToIntegralValue(priceQuantum).multiply(priceQuantum);
    }

    @Override
    public BigDecimal quantizeOrderAmount(String tradingPair, BigDecimal amount) {
        if (amount == null) {
            return null;
        }
        BigDecimal sizeQuantum = getOrderSizeQuantum(tradingPair, amount);
        return amount.divideToIntegralValue(sizeQuantum).multiply(sizeQuantum);
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public <T extends ExchangeEvent> void subscribe(Class<T> eventType, EventListener<T> listener) {
        pubSub.addListener(eventType, listener);
    }

    @Override
    public <T extends ExchangeEvent> void unsubscribe(Class<T> eventType, EventListener<T> listener) {
        pubSub.removeListener(eventType, listener);
    }
}

