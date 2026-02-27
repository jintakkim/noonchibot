package com.hotak.noonchibot.core.order;

import com.hotak.noonchibot.core.datatype.*;
import com.hotak.noonchibot.core.exception.InFlightUpdateFailedException;
import com.hotak.noonchibot.core.trade.fee.TradeFee;
import com.hotak.noonchibot.core.trade.fee.TradeFeeSchema;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class InFlightOrderTest {
    private InFlightOrder testOrder;
    private TradeFeeSchema testTradeFeeSchema;

    @BeforeEach
    void setUp() {
        testOrder = new InFlightOrder(
                "OID-123", "BTC-USDT", OrderType.LIMIT, TradeType.BUY,
                new BigDecimal("1.0"), new BigDecimal("50000.0"), Instant.now(), null);

        testTradeFeeSchema = new TradeFeeSchema(
                null,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                false,
                Collections.emptyList(),
                Collections.emptyList()
        );
    }

    @Test
    @DisplayName("주문 생성 초기화, exchangeId set이 정상적으로 작동한다.")
    void testOrderCreationState() {
        assertEquals(InFlightOrder.State.PENDING_CREATE, testOrder.getCurrentState());
        assertNull(testOrder.getExchangeOrderId());
        assertFalse(testOrder.getProcessedByExchangeEvent().isDone());

        String exchangeId = "EX-1";

        testOrder.setExchangeOrderId(exchangeId);
        testOrder.setCurrentState(InFlightOrder.State.OPEN);

        assertEquals(InFlightOrder.State.OPEN, testOrder.getCurrentState());
        assertNotNull(testOrder.getExchangeOrderId());
        assertEquals(0, BigDecimal.ZERO.compareTo(testOrder.getAverageExecutedPrice()));
        assertEquals(exchangeId, testOrder.getExchangeOrderId());
    }

    @Test
    @DisplayName("주문 취소 요청(Pending) 후 취소 확정(Cancelled)이 정상적으로 처리된다.")
    void testOrderCancellationFlow() {
        String exchangeId = "EX-1";
        testOrder.setExchangeOrderId(exchangeId);
        testOrder.setCurrentState(InFlightOrder.State.OPEN);

        testOrder.setCurrentState(InFlightOrder.State.PENDING_CANCEL);

        assertFalse(testOrder.isDone());
        assertEquals(InFlightOrder.State.PENDING_CANCEL, testOrder.getCurrentState());

        testOrder.setCurrentState(InFlightOrder.State.CANCELED);

        assertTrue(testOrder.isDone());
        assertEquals(InFlightOrder.State.CANCELED, testOrder.getCurrentState());
    }

    @Test
    @DisplayName("주문 실패(Failed) 상태 검증")
    void testOrderFailure() {
        testOrder.setCurrentState(InFlightOrder.State.FAILED);

        assertTrue(testOrder.isDone());
        assertEquals(InFlightOrder.State.FAILED, testOrder.getCurrentState());
    }

    @Test
    @DisplayName("부분 체결(Partial) 후 완전 체결(Filled) 흐름 검증")
    void testOrderFillFlow() {
        testOrder.setExchangeOrderId("EX-1");
        testOrder.setCurrentState(InFlightOrder.State.OPEN);

        testOrder.setCurrentState(InFlightOrder.State.PARTIALLY_FILLED);
        testOrder.setExecutedAmountBase(new BigDecimal("0.5"));

        assertFalse(testOrder.isDone());

        testOrder.setCurrentState(InFlightOrder.State.FILLED);
        testOrder.setExecutedAmountBase(new BigDecimal("1.0"));

        assertTrue(testOrder.isDone());
    }

    @Test
    @DisplayName("한 번에 전량 체결될 경우 평균 체결가는 주문 가격과 같다.")
    void testAveragePriceWithSingleFill() {
        BigDecimal expectedPrice = new BigDecimal("50000.0");

        TradeUpdate fill = new TradeUpdate(
                "trade-1", testOrder.getClientOrderId(), "EX-1", testOrder.getTradingPair(), Instant.now(),
                expectedPrice,
                new BigDecimal("1.0"),
                new BigDecimal("50000.0"),
                null, true
        );

        testOrder.updateWithTradeUpdate(fill);
        BigDecimal actualPrice = testOrder.getAverageExecutedPrice();

        assertNotNull(actualPrice);
        assertEquals(0, expectedPrice.compareTo(actualPrice));
    }

    @Test
    @DisplayName("주문이 두 번에 걸쳐 체결될 때, 가중 평균 가격이 정확히 계산된다.")
    void testAveragePriceWithMultipleFills() {
        TradeUpdate fill1 = new TradeUpdate(
                "trade_1", testOrder.getClientOrderId(), "EX-1", testOrder.getTradingPair(), Instant.now(),
                new BigDecimal("50000.50"),
                new BigDecimal("0.5"),
                new BigDecimal("25000.25"),
                null, false
        );
        testOrder.updateWithTradeUpdate(fill1);

        TradeUpdate fill2 = new TradeUpdate(
                "trade_2", testOrder.getClientOrderId(), "EX-1", testOrder.getTradingPair(), Instant.now(),
                new BigDecimal("50001.00"),
                new BigDecimal("0.5"),
                new BigDecimal("25000.50"),
                null, true
        );
        testOrder.updateWithTradeUpdate(fill2);

        BigDecimal expectedPrice = new BigDecimal("50000.75");
        BigDecimal actualPrice = testOrder.getAverageExecutedPrice();

        assertNotNull(actualPrice);
        assertEquals(0, expectedPrice.compareTo(actualPrice));
    }

    @Test
    @DisplayName("InFlightOrder가 LimitOrder record로 정확하게 변환되어야 한다.")
    void testToLimitOrder() {
        LimitOrder limitOrder = testOrder.toLimitOrder();

        assertNotNull(limitOrder);
        assertEquals(testOrder.getClientOrderId(), limitOrder.clientOrderId());
        assertEquals(testOrder.getTradingPair(), limitOrder.tradingPair());
        assertEquals(testOrder.getOrderType(), limitOrder.orderType());
        assertEquals(0, testOrder.getPrice().compareTo(limitOrder.price()));
        assertEquals(0, testOrder.getAmount().compareTo(limitOrder.amount()));
        assertEquals(0, BigDecimal.ZERO.compareTo(limitOrder.filledAmount()));
        assertEquals(0, testOrder.getCreationTimestamp().compareTo(limitOrder.creationTimestamp()));
    }

        @Test
        @DisplayName("Client Order ID가 불일치하면 예외가 발생하고 상태는 변경되지 않는다.")
        void testUpdateWithOrderUpdateClientOrderIdMismatch() {
            String exId = "EX-1";
            testOrder.setCurrentState(InFlightOrder.State.OPEN);
            testOrder.setExchangeOrderId(exId);

            OrderUpdate mismatchOrderUpdate = new OrderUpdate(
                    testOrder.getTradingPair(),
                    Instant.now(),
                    testOrder.getCurrentState(),
                    "wrongClientId",
                    "wrongExchangeId",
                    Map.of()
            );
            assertThrows(InFlightUpdateFailedException.class, () -> {
                testOrder.updateWithOrderUpdate(mismatchOrderUpdate);
            });
        }

        @Test
        @DisplayName("정상적인 OPEN 업데이트 시 ExchangeID와 타임스탬프가 갱신된다.")
        void testUpdateWithOrderUpdateOpenOrder() {
            String exId = "EX-1";
            testOrder.setCurrentState(InFlightOrder.State.OPEN);
            testOrder.setExchangeOrderId(exId);

            OrderUpdate validOrderUpdate = new OrderUpdate(
                    testOrder.getTradingPair(),
                    Instant.now(),
                    testOrder.getCurrentState(),
                    testOrder.getClientOrderId(),
                    exId,
                    Map.of()
            );
            testOrder.updateWithOrderUpdate(validOrderUpdate);

            assertEquals(validOrderUpdate.exchangeOrderId(), testOrder.getExchangeOrderId());
            assertEquals(InFlightOrder.State.OPEN, testOrder.getCurrentState());
        }

        @Test
        @DisplayName("상태 변경(Partial) 시 체결량은 변하지 않는다.")
        void testUpdateStateChangeOnly() {
            String exId = "EX-1";
            testOrder.setCurrentState(InFlightOrder.State.OPEN);
            testOrder.setExchangeOrderId(exId);

            OrderUpdate partialFillUpdate = new OrderUpdate(
                        testOrder.getTradingPair(),
                        Instant.now(),
                        InFlightOrder.State.PARTIALLY_FILLED,
                        testOrder.getClientOrderId(),
                        exId,
                        Map.of()
            );

            testOrder.updateWithOrderUpdate(partialFillUpdate);

            assertEquals(InFlightOrder.State.PARTIALLY_FILLED, testOrder.getCurrentState());
            assertEquals(0, BigDecimal.ZERO.compareTo(testOrder.getExecutedAmountBase()));
        }

    @Test
    @DisplayName("TradeUpdate가 적용되면 체결 수량과 TradeFee(newSpotFee)가 정상적으로 갱신된다.")
    void testUpdateWithTradeUpdateBasic() {
        TradeFee tradeFee = TradeFee.newSpotFee(
                testTradeFeeSchema,
                testOrder.getTradeType(),
                new BigDecimal("1.0"),
                "USDT",
                Collections.emptyList()
        );

        TradeUpdate tradeUpdate = new TradeUpdate(
                "trade-id-1",
                testOrder.getClientOrderId(),
                testOrder.getExchangeOrderId(),
                testOrder.getTradingPair(),
                Instant.now(),
                new BigDecimal("1.0"),
                new BigDecimal("500.0"),
                new BigDecimal("500.0"),
                tradeFee,
                true
        );

        testOrder.updateWithTradeUpdate(tradeUpdate);

        assertEquals(0, tradeUpdate.fillBaseAmount().compareTo(testOrder.getExecutedAmountBase()));
        assertEquals(0, tradeUpdate.fillQuoteAmount().compareTo(testOrder.getExecutedAmountQuote()));
        assertEquals(tradeUpdate.fillTimestamp(), testOrder.getLastUpdateTimestamp());

        BigDecimal expectedFee = new BigDecimal("5.0");
        assertEquals(0, expectedFee.compareTo(tradeUpdate.tradeFee().getTotalAmount(tradeUpdate.fillQuoteAmount())));
    }

    @Test
    @DisplayName("이미 처리된 TradeUpdate(중복 ID)는 무시되어야 한다.")
    void testUpdateWithTradeUpdateDuplicateTradeUpdate() {
        TradeFee zeroFee = TradeFee.newSpotFee(
                testTradeFeeSchema,
                testOrder.getTradeType(),
                BigDecimal.ZERO,
                "USDT",
                Collections.emptyList()
        );

        TradeUpdate tradeUpdate = new TradeUpdate(
                "duplicate-trade-id",
                testOrder.getClientOrderId(),
                testOrder.getExchangeOrderId(),
                testOrder.getTradingPair(),
                Instant.now(),
                new BigDecimal("1.0"),
                new BigDecimal("500.0"),
                new BigDecimal("500.0"),
                zeroFee,
                true
        );

        testOrder.updateWithTradeUpdate(tradeUpdate);

        assertEquals(0, new BigDecimal("500.0").compareTo(testOrder.getExecutedAmountBase()));

        assertThrows(InFlightUpdateFailedException.class, () -> {
            testOrder.updateWithTradeUpdate(tradeUpdate);
        });

        assertEquals(0, new BigDecimal("500.0").compareTo(testOrder.getExecutedAmountBase()));
    }

    @Test
    @DisplayName("여러 번의 분할 체결(Partial Fills)이 누적되어 정확히 계산된다.")
    void testUpdateWithTradeUpdateMultipleTradeUpdates() {
        TradeFee commonFee = TradeFee.newSpotFee(
                testTradeFeeSchema,
                testOrder.getTradeType(),
                BigDecimal.ZERO,
                "USDT",
                Collections.emptyList()
        );

        BigDecimal price1 = new BigDecimal("0.5");
        BigDecimal amount1 = new BigDecimal("500.0");
        TradeUpdate update1 = new TradeUpdate(
                "trade-1", testOrder.getClientOrderId(), null, testOrder.getTradingPair(), Instant.now(),
                price1, amount1, price1.multiply(amount1), commonFee, true
        );

        testOrder.updateWithTradeUpdate(update1);

        assertEquals(0, amount1.compareTo(testOrder.getExecutedAmountBase()));

        BigDecimal price2 = new BigDecimal("1.0");
        BigDecimal amount2 = new BigDecimal("500.0");
        TradeUpdate update2 = new TradeUpdate(
                "trade-2", testOrder.getClientOrderId(), null, testOrder.getTradingPair(), Instant.now(),
                price2, amount2, price2.multiply(amount2), commonFee, true
        );

        testOrder.updateWithTradeUpdate(update2);

        BigDecimal totalAmount = amount1.add(amount2); // 1000.0
        BigDecimal totalQuote = price1.multiply(amount1).add(price2.multiply(amount2)); // 750.0

        assertEquals(0, totalAmount.compareTo(testOrder.getExecutedAmountBase()));
        assertEquals(0, totalQuote.compareTo(testOrder.getExecutedAmountQuote()));

        assertEquals(0, new BigDecimal("0.75").compareTo(testOrder.getAverageExecutedPrice()));

        assertEquals(InFlightOrder.State.PENDING_CREATE, testOrder.getCurrentState());
    }

    @Test
    @DisplayName("TradeUpdate는 Exchange Order ID를 절대 변경하지 않는다.")
    void testTradeUpdateDoesNotChangeExchangeOrderId() {
        assertNull(testOrder.getExchangeOrderId());

        TradeFee zeroFee = TradeFee.newSpotFee(
                testTradeFeeSchema, testOrder.getTradeType(), BigDecimal.ZERO, "USDT", Collections.emptyList()
        );
        
        TradeUpdate tradeUpdate = new TradeUpdate(
                "trade-id-X",
                testOrder.getClientOrderId(),
                "EX-IN-TRADE",
                testOrder.getTradingPair(),
                Instant.now(),
                new BigDecimal("1.0"),
                new BigDecimal("100.0"),
                new BigDecimal("100.0"),
                zeroFee,
                true
        );
        
        testOrder.updateWithTradeUpdate(tradeUpdate);

        assertNull(testOrder.getExchangeOrderId());
        assertFalse(testOrder.getProcessedByExchangeEvent().isDone());
    }
}