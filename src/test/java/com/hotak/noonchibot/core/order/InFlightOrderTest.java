package com.hotak.noonchibot.core.order;

import com.hotak.noonchibot.core.datatype.*;
import com.hotak.noonchibot.core.exception.InFlightUpdateFailedException;
import com.hotak.noonchibot.core.trade.fee.TokenAmount;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InFlightOrderTest {

    private InFlightOrder order;

    @BeforeEach
    void setUp() {
        order = new InFlightOrder(
                "OID-123", "BTC-USDT", OrderType.LIMIT, TradeType.BUY,
                new BigDecimal("1.0"), new BigDecimal("50000.0"), Instant.now(), null);
    }

    @Nested
    @DisplayName("주문 생성 및 상태 전이")
    class OrderLifecycleTest {

        @Test
        @DisplayName("거래소 접수 후 OPEN 상태로 전이하고 exchangeOrderId가 설정된다")
        void transitionToOpen() {
            order.updateWithOrderUpdate(orderUpdate(InFlightOrder.State.OPEN, "EX-1"));

            assertThat(order.getCurrentState()).isEqualTo(InFlightOrder.State.OPEN);
            assertThat(order.getExchangeOrderId()).isEqualTo("EX-1");
            assertThat(order.isDone()).isFalse();
        }

        @Test
        @DisplayName("OPEN → PENDING_CANCEL → CANCELED 취소 흐름이 정상 처리된다")
        void cancellationFlow() {
            order.updateWithOrderUpdate(orderUpdate(InFlightOrder.State.OPEN, "EX-1"));
            order.updateWithOrderUpdate(orderUpdate(InFlightOrder.State.PENDING_CANCEL, "EX-1"));

            assertThat(order.isDone()).isFalse();

            order.updateWithOrderUpdate(orderUpdate(InFlightOrder.State.CANCELED, "EX-1"));

            assertThat(order.isDone()).isTrue();
            assertThat(order.getCurrentState()).isEqualTo(InFlightOrder.State.CANCELED);
        }

        @Test
        @DisplayName("주문 생성 실패 시 FAILED는 최종 상태이다")
        void failedIsTerminal() {
            order.updateWithOrderUpdate(orderUpdate(InFlightOrder.State.FAILED, null));

            assertThat(order.isDone()).isTrue();
            assertThat(order.getCurrentState().isTerminal()).isTrue();
        }

        @Test
        @DisplayName("부분 체결 후 완전 체결까지의 전체 흐름이 정상 처리된다")
        void partialToFullFill() {
            order.updateWithOrderUpdate(orderUpdate(InFlightOrder.State.OPEN, "EX-1"));

            order.updateWithTradeUpdate(createFill("t-1", "50000.0", "0.5", "25000.0", List.of()));
            order.updateWithOrderUpdate(orderUpdate(InFlightOrder.State.PARTIALLY_FILLED, "EX-1"));

            assertThat(order.isDone()).isFalse();
            assertThat(order.getExecutedAmountBase()).isEqualByComparingTo(new BigDecimal("0.5"));

            order.updateWithTradeUpdate(createFill("t-2", "50000.0", "0.5", "25000.0", List.of()));
            order.updateWithOrderUpdate(orderUpdate(InFlightOrder.State.FILLED, "EX-1"));

            assertThat(order.isDone()).isTrue();
            assertThat(order.getExecutedAmountBase()).isEqualByComparingTo(new BigDecimal("1.0"));
            assertThat(order.isOrderFilled()).isTrue();
        }

        private OrderUpdateEvent orderUpdate(InFlightOrder.State state, String exchangeOrderId) {
            return new OrderUpdateEvent("BTC-USDT", Instant.now(), state, "OID-123", exchangeOrderId, null);
        }
    }

    @Nested
    @DisplayName("OrderUpdateEvent 적용")
    class OrderUpdateEventTest {

        @Test
        @DisplayName("clientOrderId가 일치하면 exchangeOrderId와 상태가 갱신된다")
        void validUpdate() {
            OrderUpdateEvent update = new OrderUpdateEvent(
                    "BTC-USDT", Instant.now(), InFlightOrder.State.PARTIALLY_FILLED,
                    "OID-123", "EX-1", null
            );
            order.updateWithOrderUpdate(update);

            assertThat(order.getExchangeOrderId()).isEqualTo("EX-1");
            assertThat(order.getCurrentState()).isEqualTo(InFlightOrder.State.PARTIALLY_FILLED);
        }


        @Test
        @DisplayName("clientOrderId가 불일치하면 예외가 발생한다")
        void rejectsClientOrderIdMismatch() {
            OrderUpdateEvent mismatch = new OrderUpdateEvent(
                    "BTC-USDT", Instant.now(), InFlightOrder.State.FILLED,
                    "WRONG-ID", "WRONG-EX", null
            );

            assertThatThrownBy(() -> order.updateWithOrderUpdate(mismatch))
                    .isInstanceOf(InFlightUpdateFailedException.class);
        }
    }

    @Nested
    @DisplayName("TradeUpdateEvent 적용")
    class TradeUpdateEventTest {

        @Test
        @DisplayName("체결 수량과 금액이 누적된다")
        void accumulatesFills() {
            TradeUpdateEvent fill = createFill("trade-1", "50000.0", "0.6", "30000.0", List.of());
            order.updateWithTradeUpdate(fill);

            assertThat(order.getExecutedAmountBase()).isEqualByComparingTo(new BigDecimal("0.6"));
            assertThat(order.getExecutedAmountQuote()).isEqualByComparingTo(new BigDecimal("30000.0"));
        }

        @Test
        @DisplayName("중복 tradeId는 무시되고 체결량이 중복 누적되지 않는다")
        void ignoresDuplicateTradeId() {
            TradeUpdateEvent fill = createFill("dup-trade", "50000.0", "0.5", "25000.0", List.of());
            order.updateWithTradeUpdate(fill);
            order.updateWithTradeUpdate(fill);
            assertThat(order.getExecutedAmountBase()).isEqualByComparingTo(new BigDecimal("0.5"));
        }

        @Test
        @DisplayName("TradeUpdate는 exchangeOrderId를 변경하지 않는다")
        void doesNotChangeExchangeOrderId() {
            assertThat(order.getExchangeOrderId()).isNull();
            TradeUpdateEvent fill = new TradeUpdateEvent(
                    "trade-1", "OID-123", "EX-IN-TRADE", "BTC-USDT",
                    Instant.now(), new BigDecimal("50000.0"), new BigDecimal("1.0"),
                    new BigDecimal("50000.0"), List.of(), true
            );
            order.updateWithTradeUpdate(fill);
            assertThat(order.getExchangeOrderId()).isNull();
        }
    }

    @Nested
    @DisplayName("평균 체결가 계산")
    class AveragePriceTest {

        @Test
        @DisplayName("체결이 없으면 null을 반환한다")
        void returnsNullWhenNoFills() {
            assertThat(order.getAverageExecutedPrice()).isNull();
        }

        @Test
        @DisplayName("단일 체결 시 체결가와 동일하다")
        void singleFill() {
            order.updateWithTradeUpdate(createFill("t-1", "50000.0", "1.0", "50000.0", List.of()));
            assertThat(order.getAverageExecutedPrice()).isEqualByComparingTo(new BigDecimal("50000.0"));
        }

        @Test
        @DisplayName("두 번 분할 체결 시 가중 평균이 정확하다")
        void weightedAverageWithTwoFills() {
            // 0.5 BTC @ 50000.50 = 25000.25
            order.updateWithTradeUpdate(createFill("t-1", "50000.50", "0.5", "25000.25", List.of()));
            // 0.5 BTC @ 50001.00 = 25000.50
            order.updateWithTradeUpdate(createFill("t-2", "50001.00", "0.5", "25000.50", List.of()));
            // 총 50000.75 / 1.0 = 50000.75
            assertThat(order.getAverageExecutedPrice()).isEqualByComparingTo(new BigDecimal("50000.75"));
        }
    }

    @Nested
    @DisplayName("수수료 계산")
    class FeeTest {

        @Test
        @DisplayName("체결이 없으면 수수료는 0이다")
        void zeroFeeWhenNoFills() {
            assertThat(order.getCumulativeFeePaid()).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("단일 토큰 수수료가 정확히 합산된다")
        void singleTokenFee() {
            List<TokenAmount> fee1 = List.of(new TokenAmount("USDT", new BigDecimal("25.0")));
            List<TokenAmount> fee2 = List.of(new TokenAmount("USDT", new BigDecimal("25.0")));

            order.updateWithTradeUpdate(createFill("t-1", "50000.0", "0.5", "25000.0", fee1));
            order.updateWithTradeUpdate(createFill("t-2", "50000.0", "0.5", "25000.0", fee2));

            assertThat(order.getCumulativeFeePaid()).isEqualByComparingTo(new BigDecimal("50.0"));
        }

        @Test
        @DisplayName("한 체결에 여러 토큰 수수료가 있으면 전부 합산된다")
        void multiTokenFee() {
            List<TokenAmount> fee = List.of(
                    new TokenAmount("BNB", new BigDecimal("0.001")),
                    new TokenAmount("USDT", new BigDecimal("5.0"))
            );
            order.updateWithTradeUpdate(createFill("t-1", "50000.0", "1.0", "50000.0", fee));
            assertThat(order.getCumulativeFeePaid()).isEqualByComparingTo(new BigDecimal("5.001"));
        }

        @Test
        @DisplayName("수수료가 빈 리스트이면 0이다")
        void emptyFeeList() {
            order.updateWithTradeUpdate(createFill("t-1", "50000.0", "1.0", "50000.0", List.of()));
            assertThat(order.getCumulativeFeePaid()).isEqualByComparingTo(BigDecimal.ZERO);
        }
    }

    @Nested
    @DisplayName("주문 완전 체결 판정")
    class OrderFilledTest {

        @Test
        @DisplayName("체결량이 주문량과 같으면 체결 완료이다")
        void filledWhenAmountMatches() {
            order.updateWithTradeUpdate(createFill("t-1", "50000.0", "1.0", "50000.0", List.of()));
            assertThat(order.isOrderFilled()).isTrue();
        }

        @Test
        @DisplayName("체결량이 허용 오차 이내이면 체결 완료로 판정한다")
        void filledWithinTolerance() {
            order.updateWithTradeUpdate(createFill("t-1", "50000.0", "0.99999999", "49999.9995", List.of()));
            assertThat(order.isOrderFilled()).isTrue();
        }

        @Test
        @DisplayName("체결량이 부족하면 미체결이다")
        void notFilledWhenShort() {
            order.updateWithTradeUpdate(createFill("t-1", "50000.0", "0.5", "25000.0", List.of()));
            assertThat(order.isOrderFilled()).isFalse();
        }
    }

    @Nested
    @DisplayName("LimitOrder 변환")
    class ToLimitOrderTest {
        @Test
        @DisplayName("InFlightOrder의 필드가 LimitOrder로 정확히 매핑된다")
        void mapsFieldsCorrectly() {
            LimitOrder limitOrder = order.toLimitOrder();

            assertThat(limitOrder.clientOrderId()).isEqualTo("OID-123");
            assertThat(limitOrder.tradingPair()).isEqualTo("BTC-USDT");
            assertThat(limitOrder.orderType()).isEqualTo(OrderType.LIMIT);
            assertThat(limitOrder.price()).isEqualByComparingTo(new BigDecimal("50000.0"));
            assertThat(limitOrder.amount()).isEqualByComparingTo(new BigDecimal("1.0"));
            assertThat(limitOrder.filledAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        }
    }

    // === 헬퍼 ===

    private TradeUpdateEvent createFill(String tradeId, String price, String baseAmount, String quoteAmount, List<TokenAmount> fee) {
        return new TradeUpdateEvent(
                tradeId, "OID-123", "EX-1", "BTC-USDT",
                Instant.now(),
                new BigDecimal(price),
                new BigDecimal(baseAmount),
                new BigDecimal(quoteAmount),
                fee,
                true
        );
    }
}
