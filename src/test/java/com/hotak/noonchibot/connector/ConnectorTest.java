package com.hotak.noonchibot.connector;

import com.hotak.noonchibot.core.datatype.InFlightOrder;
import com.hotak.noonchibot.core.order.OrderType;
import com.hotak.noonchibot.core.datatype.TradeType;
import com.hotak.noonchibot.core.datatype.TradeUpdate;
import com.hotak.noonchibot.core.event.EventLogger;
import com.hotak.noonchibot.core.event.OrderFilledEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public abstract class ConnectorTest {
    abstract Connector getConnector(Map<String, BigDecimal> balanceLimit);
    abstract EventLogger getEventLogger(Connector connector);
    abstract void setAvailableBalance(Connector connector, String tradingPair, BigDecimal amount);
    abstract void addInFlightOrder(Connector connector, InFlightOrder order);
    abstract void setOrderPriceQuantum(Connector connector, String tradingPair, BigDecimal quantum);
    abstract void setOrderSizeQuantum(Connector connector, String tradingPair, BigDecimal quantum);


    InFlightOrder createInFlightOrder(String orderId, String tradingPair, TradeType tradeType,  BigDecimal amount, BigDecimal price, Instant creationTimestamp) {
        return new InFlightOrder(orderId, tradingPair, OrderType.LIMIT, tradeType, amount, price, creationTimestamp);
    }


    @Test
    @DisplayName("제한 없으면 실제 잔고 반환")
    void returnsActualBalance() {
        Connector connector = getConnector(null);
        setAvailableBalance(connector, "USDT", new BigDecimal("10000"));
        BigDecimal result = connector.getAvailableBalance("USDT");
        assertThat(result).isEqualByComparingTo("10000");
    }

    @Test
    @DisplayName("실제 잔고가 제한보다 크면 제한 반환")
    void returnsLimitWhenBalanceIsHigher() {
        Map<String, BigDecimal> balanceLimit = Map.of("USDT", new BigDecimal("1000"), "ETH", new BigDecimal("2"));
        Connector connector = getConnector(balanceLimit);
        setAvailableBalance(connector, "USDT", new BigDecimal("10000"));

        BigDecimal result = connector.getAvailableBalance("USDT");

        assertThat(result).isEqualByComparingTo("1000");
    }

    @Test
    @DisplayName("실제 잔고가 제한보다 작으면 실제 잔고 반환")
    void returnsBalanceWhenLimitIsHigher() {
        Map<String, BigDecimal> balanceLimit = Map.of("USDT", new BigDecimal("1000"), "ETH", new BigDecimal("2"));
        Connector connector = getConnector(balanceLimit);
        setAvailableBalance(connector,"USDT" , new BigDecimal("500"));
        BigDecimal result = connector.getAvailableBalance("USDT");
        assertThat(result).isEqualByComparingTo("500");
    }

    @Test
    @DisplayName("미체결 주문이 있으면 제한에서 차감")
    void subtractsInFlightOrders() {
        Map<String, BigDecimal> balanceLimit = Map.of("USDT", new BigDecimal("1000"), "ETH", new BigDecimal("2"));
        Connector connector = getConnector(balanceLimit);
        setAvailableBalance(connector, "USDT", new BigDecimal("10000"));
        // 500 USDT 상당의 매수 주문
        InFlightOrder buyOrder = createInFlightOrder("1", "ETH-USDT", TradeType.BUY, BigDecimal.ONE, BigDecimal.valueOf(500), Instant.parse("2022-12-04T08:07:00Z"));
        addInFlightOrder(connector, buyOrder);
        BigDecimal result = connector.getAvailableBalance("USDT");
        assertThat(result).isEqualByComparingTo("500");
    }

    @Test
    @DisplayName("체결된 주문이 있으면 제한에 반영")
    void addsFilledOrders() {
        Map<String, BigDecimal> balanceLimit = Map.of("USDT", new BigDecimal("1000"), "ETH", new BigDecimal("2"));
        Connector connector = getConnector(balanceLimit);
        setAvailableBalance(connector, "USDT", new BigDecimal("10000"));
        EventLogger eventLogger = getEventLogger(connector);
        eventLogger.onEvent(new OrderFilledEvent(Instant.parse("2022-12-04T08:07:00Z"), "1", "ETH-USDT", TradeType.SELL, OrderType.MARKET, BigDecimal.valueOf(1000), BigDecimal.ONE, null, null, null, null, null));
        // 1000 USDT 매도로 획득
        BigDecimal result = connector.getAvailableBalance("USDT");
        // 제한 1000 + 체결 1000 = 2000, 실제 잔고 10000 -> min = 2000
        assertThat(result).isEqualByComparingTo("2000");
    }

    @Test
    @DisplayName("미체결 + 체결 복합 시나리오")
    void combinedInFlightAndFilled() {
        Map<String, BigDecimal> balanceLimit = Map.of("USDT", new BigDecimal("1000"), "ETH", new BigDecimal("2"));
        Connector connector = getConnector(balanceLimit);
        setAvailableBalance(connector, "USDT", new BigDecimal("10000"));

        // 300 USDT 매수 주문
        InFlightOrder buyOrder = createInFlightOrder("1", "BTC-USDT", TradeType.BUY, new BigDecimal("0.01"), BigDecimal.valueOf(30000), Instant.parse("2022-12-04T08:07:00Z"));
        addInFlightOrder(connector, buyOrder);
        EventLogger eventLogger = getEventLogger(connector);
        eventLogger.onEvent(new OrderFilledEvent(Instant.parse("2022-12-04T08:07:00Z"), "1", "ETH-USDT", TradeType.SELL, OrderType.MARKET, BigDecimal.valueOf(500), BigDecimal.ONE, null, null, null, null, null));
        // 500 USDT 매도로 획득
        BigDecimal result = connector.getAvailableBalance("USDT");
        assertThat(result).isEqualByComparingTo("1200");
    }

    @Test
    @DisplayName("제한이 0 미만이 되면 0 반환")
    void floorAtZero() {
        Map<String, BigDecimal> balanceLimit = Map.of("USDT", new BigDecimal("1000"), "ETH", new BigDecimal("2"));
        Connector connector = getConnector(balanceLimit);
        setAvailableBalance(connector, "USDT", new BigDecimal("10000"));

        // 2000 USDT 상당의 큰 매수 주문 (제한 1000 초과)
        InFlightOrder buyOrder = createInFlightOrder("1", "BTC-USDT", TradeType.BUY, new BigDecimal("0.04"), BigDecimal.valueOf(50000), Instant.parse("2022-12-04T08:07:00Z"));
        addInFlightOrder(connector, buyOrder);
        BigDecimal result = connector.getAvailableBalance("USDT");
        assertThat(result).isEqualByComparingTo("0");
    }

    @Nested
    @DisplayName("getInFlightAssetBalances")
    class InFlightAssetBalancesTest {

        @Test
        @DisplayName("매수 주문 - Quote 자산 잠김")
        void buyOrderLocksQuoteAsset() {
            Connector connector = getConnector(null);
            // BTC 매수: USDT가 잠김
            InFlightOrder buyOrder = createInFlightOrder("1", "BTC-USDT", TradeType.BUY, new BigDecimal("0.1"), BigDecimal.valueOf(50000), Instant.parse("2022-12-04T08:07:00Z"));
            addInFlightOrder(connector, buyOrder);
            Map<String, BigDecimal> inFlightAssets = connector.getInFlightAssetBalances();

            assertThat(inFlightAssets.get("USDT")).isEqualByComparingTo("5000");
            assertThat(inFlightAssets.get("BTC")).isNull();
        }

        @Test
        @DisplayName("매도 주문 - Base 자산 잠김")
        void sellOrderLocksBaseAsset() {
            Connector connector = getConnector(null);
            // BTC 매도: BTC가 잠김
            InFlightOrder sellOrder = createInFlightOrder("1", "BTC-USDT", TradeType.SELL, new BigDecimal("0.5"), BigDecimal.valueOf(50000), Instant.parse("2022-12-04T08:07:00Z"));
            addInFlightOrder(connector, sellOrder);

            Map<String, BigDecimal> inFlight = connector.getInFlightAssetBalances();

            assertThat(inFlight.get("BTC")).isEqualByComparingTo("0.5");
            assertThat(inFlight.get("USDT")).isNull();
        }

        @Test
        @DisplayName("부분 체결된 주문은 미체결 수량만 계산")
        void partiallyFilledOrder() {
            Connector connector = getConnector(null);
            // 1 BTC 주문 중 0.3 BTC 체결됨
            InFlightOrder sellOrder = createInFlightOrder("1", "BTC-USDT", TradeType.SELL, new BigDecimal("1"), BigDecimal.valueOf(50000), Instant.parse("2022-12-04T08:07:00Z"));
            sellOrder.updateWithTradeUpdate(new TradeUpdate(
                    "trade-1",           // tradeId
                    sellOrder.getClientOrderId(),  // clientOrderId
                    "exchange-order-1",  // exchangeOrderId
                    "BTC-USDT",          // tradingPair
                    Instant.now(),        // fillTimestamp
                    new BigDecimal("50000"),   // fillPrice
                    new BigDecimal("0.3"),     // fillBaseAmount (체결된 BTC)
                    new BigDecimal("15000"),   // fillQuoteAmount (체결된 USDT)
                    null,
                    true
            ));
            addInFlightOrder(connector, sellOrder);

            Map<String, BigDecimal> inFlight = connector.getInFlightAssetBalances();
            // 미체결: 0.7 BTC * 50000 * 1.001 = 35035
            assertThat(inFlight.get("USDT")).isEqualByComparingTo("35035");
        }

        @Test
        @DisplayName("완료된 주문은 무시")
        void ignoresDoneOrders() {
            Connector connector = getConnector(null);
            InFlightOrder sellOrder = createInFlightOrder("1", "BTC-USDT", TradeType.SELL, new BigDecimal("1"), BigDecimal.valueOf(50000), Instant.parse("2022-12-04T08:07:00Z"));
            sellOrder.updateWithTradeUpdate(new TradeUpdate(
                    "trade-1",           // tradeId
                    sellOrder.getClientOrderId(),  // clientOrderId
                    "exchange-order-1",  // exchangeOrderId
                    "BTC-USDT",          // tradingPair
                    Instant.now(),        // fillTimestamp
                    new BigDecimal("50000"),   // fillPrice
                    new BigDecimal("1"),     // fillBaseAmount (체결된 BTC)
                    new BigDecimal("50000"),   // fillQuoteAmount (체결된 USDT)
                    null,
                    true
            ));
            addInFlightOrder(connector, sellOrder);
            Map<String, BigDecimal> inFlight = connector.getInFlightAssetBalances();
            assertThat(inFlight).isEmpty();
        }

        @Test
        @DisplayName("여러 주문 누적")
        void accumulatesMultipleOrders() {
            Connector connector = getConnector(null);
            InFlightOrder sellOrder1 = createInFlightOrder("1", "BTC-USDT", TradeType.SELL, new BigDecimal("0.1"), BigDecimal.valueOf(50000), Instant.parse("2022-12-04T08:07:00Z"));
            InFlightOrder sellOrder2 = createInFlightOrder("2", "ETH-USDT", TradeType.SELL, new BigDecimal("1"), BigDecimal.valueOf(2000), Instant.parse("2022-12-04T08:07:00Z"));

            addInFlightOrder(connector, sellOrder1);
            addInFlightOrder(connector, sellOrder2);

            Map<String, BigDecimal> inFlight = connector.getInFlightAssetBalances();
            assertThat(inFlight.get("USDT")).isEqualByComparingTo("7007");
        }
    }

    @Nested
    @DisplayName("getOrderFilledBalances")
    class OrderFilledBalancesTest {
        @Test
        @DisplayName("매수 체결 - Base 증가, Quote 감소")
        void buyFillIncreasesBaseDecreasesQuote() {
            Connector connector = getConnector(null);
            EventLogger eventLogger = getEventLogger(connector);
            eventLogger.onEvent(new OrderFilledEvent(Instant.parse("2022-12-04T08:07:00Z"), "1", "BTC-USDT", TradeType.BUY, OrderType.MARKET, BigDecimal.valueOf(50000), BigDecimal.ONE, null, null, null, null, null));
            Map<String, BigDecimal> balances = connector.getOrderFilledBalances();

            assertThat(balances.get("BTC")).isEqualByComparingTo("1");
            assertThat(balances.get("USDT")).isEqualByComparingTo("-50000");
        }

        @Test
        @DisplayName("매도 체결 - Base 감소, Quote 증가")
        void sellFillDecreasesBaseIncreasesQuote() {
            Connector connector = getConnector(null);
            EventLogger eventLogger = getEventLogger(connector);
            eventLogger.onEvent(new OrderFilledEvent(Instant.parse("2022-12-04T08:07:00Z"), "1", "BTC-USDT", TradeType.SELL, OrderType.MARKET, BigDecimal.valueOf(50000), BigDecimal.ONE, null, null, null, null, null));
            Map<String, BigDecimal> balances = connector.getOrderFilledBalances();

            assertThat(balances.get("BTC")).isEqualByComparingTo("-1");
            assertThat(balances.get("USDT")).isEqualByComparingTo("50000");
        }

        @Test
        @DisplayName("여러 체결 누적")
        void accumulatesMultipleFills() {
            Connector connector = getConnector(null);
            EventLogger eventLogger = getEventLogger(connector);
            eventLogger.onEvent(new OrderFilledEvent(Instant.parse("2022-12-04T08:07:00Z"), "1", "BTC-USDT", TradeType.BUY, OrderType.MARKET, BigDecimal.valueOf(50000), BigDecimal.ONE, null, null, null, null, null));
            eventLogger.onEvent(new OrderFilledEvent(Instant.parse("2022-12-04T08:07:00Z"), "1", "BTC-USDT", TradeType.BUY, OrderType.MARKET, BigDecimal.valueOf(51000), new BigDecimal("0.5"), null, null, null, null, null));
            eventLogger.onEvent(new OrderFilledEvent(Instant.parse("2022-12-04T08:07:00Z"), "1", "BTC-USDT", TradeType.BUY, OrderType.MARKET, BigDecimal.valueOf(52000), new BigDecimal("0.3"), null, null, null, null, null));
            Map<String, BigDecimal> balances = connector.getOrderFilledBalances();
            // BTC: 1 + 0.5 - 0.3 = 1.2
            assertThat(balances.get("BTC")).isEqualByComparingTo("1.2");
            // USDT: -50000 - 25500 + 15600 = -59900
            assertThat(balances.get("USDT")).isEqualByComparingTo("-59900");
        }
    }

    @Nested
    @DisplayName("quantizeOrderPrice / quantizeOrderAmount")
    class QuantizeTest {
        @Test
        @DisplayName("가격 양자화 - 내림 적용")
        void quantizePrice() {
            Connector connector = getConnector(null);
            // tick size = 0.01
            setOrderPriceQuantum(connector, "BTC-USDT", new BigDecimal("0.01"));
            BigDecimal result = connector.quantizeOrderPrice("BTC-USDT", new BigDecimal("50123.456"));
            assertThat(result).isEqualByComparingTo("50123.45");
        }

        @Test
        @DisplayName("가격 양자화 - 정확히 나누어 떨어지는 경우")
        void quantizePriceExact() {
            Connector connector = getConnector(null);
            setOrderPriceQuantum(connector, "BTC-USDT", new BigDecimal("0.01"));
            BigDecimal result = connector.quantizeOrderPrice("BTC-USDT", new BigDecimal("50123.45"));
            assertThat(result).isEqualByComparingTo("50123.45");
        }

        @Test
        @DisplayName("가격 양자화 - 큰 tick size")
        void quantizePriceLargeTick() {
            Connector connector = getConnector(null);
            // tick size = 10
            setOrderPriceQuantum(connector, "BTC-USDT", new BigDecimal("10"));
            BigDecimal result = connector.quantizeOrderPrice("BTC-USDT", new BigDecimal("50123.456"));
            assertThat(result).isEqualByComparingTo("50120");
        }

        @Test
        @DisplayName("가격 양자화 - null 입력")
        void quantizePriceNull() {
            Connector connector = getConnector(null);
            BigDecimal result = connector.quantizeOrderPrice("BTC-USDT", null);
            assertThat(result).isNull();
        }

        @Test
        @DisplayName("수량 양자화 - 내림 적용")
        void quantizeAmount() {
            Connector connector = getConnector(null);
            // lot size = 0.0001
            setOrderSizeQuantum(connector, "BTC-USDT", new BigDecimal("0.0001"));
            BigDecimal result = connector.quantizeOrderAmount("BTC-USDT", new BigDecimal("1.23456789"));
            assertThat(result).isEqualByComparingTo("1.2345");
        }

        @Test
        @DisplayName("수량 양자화 - 정확히 나누어 떨어지는 경우")
        void quantizeAmountExact() {
            Connector connector = getConnector(null);
            setOrderSizeQuantum(connector, "BTC-USDT", new BigDecimal("0.0001"));
            BigDecimal result = connector.quantizeOrderAmount("BTC-USDT", new BigDecimal("1.2345"));
            assertThat(result).isEqualByComparingTo("1.2345");
        }

        @Test
        @DisplayName("수량 양자화 - 큰 lot size")
        void quantizeAmountLargeLot() {
            Connector connector = getConnector(null);
            // lot size = 0.1
            setOrderSizeQuantum(connector, "BTC-USDT", new BigDecimal("0.1"));
            BigDecimal result = connector.quantizeOrderAmount("BTC-USDT", new BigDecimal("1.2345"));
            assertThat(result).isEqualByComparingTo("1.2");
        }

        @Test
        @DisplayName("수량 양자화 - null 입력")
        void quantizeAmountNull() {
            Connector connector = getConnector(null);
            BigDecimal result = connector.quantizeOrderAmount("BTC-USDT", null);
            assertThat(result).isNull();
        }

        @Test
        @DisplayName("수량이 lot size보다 작은 경우 0 반환")
        void quantizeAmountSmallerThanLot() {
            Connector connector = getConnector(null);
            setOrderSizeQuantum(connector, "BTC-USDT", new BigDecimal("0.01"));
            BigDecimal result = connector.quantizeOrderAmount("BTC-USDT", new BigDecimal("0.005"));
            assertThat(result).isEqualByComparingTo("0");
        }
    }
}
