package com.hotak.noonchibot.core.order;

import com.hotak.noonchibot.core.exception.InFlightUpdateFailedException;
import com.hotak.noonchibot.core.trade.TokenAmount;
import com.hotak.noonchibot.core.trade.TradeType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InFlightOrderTest {

    private InFlightOrder inFlightOrder;

    @BeforeEach
    void setUp() {
        inFlightOrder = new InFlightOrder(
                "OID-123",
                "BTC-USDT",
                OrderType.LIMIT,
                TradeType.BUY,
                new BigDecimal("1.0"),
                new BigDecimal("50000.0"),
                Instant.now(),
                false,
                TimeInForce.IOC
        );
    }

    @Nested
    @DisplayName("주문 생성 및 상태 전이")
    class InFlightOrderLifecycleTest {

        @Test
        @DisplayName("거래소 접수 후 OPEN 상태로 전이하고 exchangeOrderId가 설정된다")
        void transitionToOpen() {
            inFlightOrder.updateWithOrderUpdate(orderUpdate(OrderState.OPEN, "EX-1"));

            assertThat(inFlightOrder.getCurrentState()).isEqualTo(OrderState.OPEN);
            assertThat(inFlightOrder.getExchangeOrderId()).isEqualTo("EX-1");
            assertThat(inFlightOrder.isDone()).isFalse();
        }

        @Test
        @DisplayName("OPEN → PENDING_CANCEL → CANCELED 취소 흐름이 정상 처리된다")
        void cancellationFlow() {
            inFlightOrder.updateWithOrderUpdate(orderUpdate(OrderState.OPEN, "EX-1"));
            inFlightOrder.updateWithOrderUpdate(orderUpdate(OrderState.PENDING_CANCEL, "EX-1"));

            assertThat(inFlightOrder.isDone()).isFalse();

            inFlightOrder.updateWithOrderUpdate(orderUpdate(OrderState.CANCELED, "EX-1"));

            assertThat(inFlightOrder.isDone()).isTrue();
            assertThat(inFlightOrder.getCurrentState()).isEqualTo(OrderState.CANCELED);
        }

        @Test
        @DisplayName("주문 생성 실패 시 FAILED는 최종 상태이다")
        void failedIsTerminal() {
            inFlightOrder.updateWithOrderUpdate(orderUpdate(OrderState.FAILED, null));

            assertThat(inFlightOrder.isDone()).isTrue();
            assertThat(inFlightOrder.getCurrentState().isTerminal()).isTrue();
        }

        @Test
        @DisplayName("부분 체결 후 완전 체결까지의 전체 흐름이 정상 처리된다")
        void partialToFullFill() {
            inFlightOrder.updateWithOrderUpdate(orderUpdate(OrderState.OPEN, "EX-1"));

            inFlightOrder.updateWithTradeUpdate(createFill("t-1", "50000.0", "0.5", "25000.0", null));
            inFlightOrder.updateWithOrderUpdate(orderUpdate(OrderState.PARTIALLY_FILLED, "EX-1"));

            assertThat(inFlightOrder.isDone()).isFalse();
            assertThat(inFlightOrder.getExecutedAmountBase()).isEqualByComparingTo(new BigDecimal("0.5"));

            inFlightOrder.updateWithTradeUpdate(createFill("t-2", "50000.0", "0.5", "25000.0", null));
            inFlightOrder.updateWithOrderUpdate(orderUpdate(OrderState.FILLED, "EX-1"));

            assertThat(inFlightOrder.isDone()).isTrue();
            assertThat(inFlightOrder.getExecutedAmountBase()).isEqualByComparingTo(new BigDecimal("1.0"));
            assertThat(inFlightOrder.isOrderFilled()).isTrue();
        }

        private OrderUpdateDto orderUpdate(OrderState state, String exchangeOrderId) {
            return new OrderUpdateDto("BTC-USDT", Instant.now(), state, "OID-123", exchangeOrderId, null);
        }
    }

    @Nested
    @DisplayName("OrderUpdateDto 적용")
    class InFlightOrderUpdateEventTest {

        @Test
        @DisplayName("clientOrderId가 일치하면 exchangeOrderId와 상태가 갱신된다")
        void validUpdate() {
            OrderUpdateDto update = new OrderUpdateDto(
                    "BTC-USDT", Instant.now(), OrderState.PARTIALLY_FILLED,
                    "OID-123", "EX-1", null
            );
            inFlightOrder.updateWithOrderUpdate(update);

            assertThat(inFlightOrder.getExchangeOrderId()).isEqualTo("EX-1");
            assertThat(inFlightOrder.getCurrentState()).isEqualTo(OrderState.PARTIALLY_FILLED);
        }


        @Test
        @DisplayName("clientOrderId가 불일치하면 예외가 발생한다")
        void rejectsClientOrderIdMismatch() {
            OrderUpdateDto mismatch = new OrderUpdateDto(
                    "BTC-USDT", Instant.now(), OrderState.FILLED,
                    "WRONG-ID", "WRONG-EX", null
            );

            assertThatThrownBy(() -> inFlightOrder.updateWithOrderUpdate(mismatch))
                    .isInstanceOf(InFlightUpdateFailedException.class);
        }
    }

    @Nested
    @DisplayName("TradeUpdateEvent 적용")
    class TradeUpdateEventTest {

        @Test
        @DisplayName("체결 수량과 금액이 누적된다")
        void accumulatesFills() {
            TradeUpdateEvent fill = createFill("trade-1", "50000.0", "0.6", "30000.0", null);
            inFlightOrder.updateWithTradeUpdate(fill);

            assertThat(inFlightOrder.getExecutedAmountBase()).isEqualByComparingTo(new BigDecimal("0.6"));
            assertThat(inFlightOrder.getExecutedAmountQuote()).isEqualByComparingTo(new BigDecimal("30000.0"));
        }

        @Test
        @DisplayName("TradeUpdate는 exchangeOrderId를 변경하지 않는다")
        void doesNotChangeExchangeOrderId() {
            assertThat(inFlightOrder.getExchangeOrderId()).isNull();
            TradeUpdateEvent fill = new TradeUpdateEvent(
                    "trade-1", "OID-123", "EX-IN-TRADE", "BTC-USDT",
                    Instant.now(), new BigDecimal("50000.0"), new BigDecimal("1.0"),
                    new BigDecimal("50000.0"), new TokenAmount("USDT", BigDecimal.ZERO), true
            );
            inFlightOrder.updateWithTradeUpdate(fill);
            assertThat(inFlightOrder.getExchangeOrderId()).isNull();
        }
    }

    @Nested
    @DisplayName("평균 체결가 계산")
    class AveragePriceTest {

        @Test
        @DisplayName("체결이 없으면 null을 반환한다")
        void returnsNullWhenNoFills() {
            assertThat(inFlightOrder.getAverageExecutedPrice()).isNull();
        }

        @Test
        @DisplayName("단일 체결 시 체결가와 동일하다")
        void singleFill() {
            inFlightOrder.updateWithTradeUpdate(createFill("t-1", "50000.0", "1.0", "50000.0", null));
            assertThat(inFlightOrder.getAverageExecutedPrice()).isEqualByComparingTo(new BigDecimal("50000.0"));
        }

        @Test
        @DisplayName("두 번 분할 체결 시 가중 평균이 정확하다")
        void weightedAverageWithTwoFills() {
            // 0.5 BTC @ 50000.50 = 25000.25
            inFlightOrder.updateWithTradeUpdate(createFill("t-1", "50000.50", "0.5", "25000.25", null));
            // 0.5 BTC @ 50001.00 = 25000.50
            inFlightOrder.updateWithTradeUpdate(createFill("t-2", "50001.00", "0.5", "25000.50", null));
            // 총 50000.75 / 1.0 = 50000.75
            assertThat(inFlightOrder.getAverageExecutedPrice()).isEqualByComparingTo(new BigDecimal("50000.75"));
        }
    }

    @Nested
    @DisplayName("수수료 계산")
    class FeeTest {

        @Test
        @DisplayName("체결이 없으면 수수료는 0이다")
        void zeroFeeWhenNoFills() {
            assertThat(inFlightOrder.getAccumulatedFees()).isEmpty();
        }

        @Test
        @DisplayName("단일 토큰 수수료가 정확히 합산된다")
        void singleTokenFee() {
            TokenAmount fee1 = new TokenAmount("USDT", new BigDecimal("25.0"));
            TokenAmount fee2 = new TokenAmount("USDT", new BigDecimal("25.0"));

            inFlightOrder.updateWithTradeUpdate(createFill("t-1", "50000.0", "0.5", "25000.0", fee1));
            inFlightOrder.updateWithTradeUpdate(createFill("t-2", "50000.0", "0.5", "25000.0", fee2));

            assertThat(inFlightOrder.getAccumulatedFees().get("USDT")).isEqualByComparingTo(new BigDecimal("50.0"));
        }

        @Test
        @DisplayName("수수료가 null이면 수수료로 추가하지 않는다.")
        void emptyFeeList() {
            inFlightOrder.updateWithTradeUpdate(createFill("t-1", "50000.0", "1.0", "50000.0", null));
            assertThat(inFlightOrder.getAccumulatedFees()).isEmpty();
        }

    }

    @Nested
    @DisplayName("주문 완전 체결 판정")
    class InFlightOrderFilledTest {

        @Test
        @DisplayName("체결량이 주문량과 같으면 체결 완료이다")
        void filledWhenAmountMatches() {
            inFlightOrder.updateWithTradeUpdate(createFill("t-1", "50000.0", "1.0", "50000.0", null));
            assertThat(inFlightOrder.isOrderFilled()).isTrue();
        }

        @Test
        @DisplayName("체결량이 허용 오차 이내이면 체결 완료로 판정한다")
        void filledWithinTolerance() {
            inFlightOrder.updateWithTradeUpdate(createFill("t-1", "50000.0", "0.99999999", "49999.9995", null));
            assertThat(inFlightOrder.isOrderFilled()).isTrue();
        }

        @Test
        @DisplayName("체결량이 부족하면 미체결이다")
        void notFilledWhenShort() {
            inFlightOrder.updateWithTradeUpdate(createFill("t-1", "50000.0", "0.5", "25000.0", null));
            assertThat(inFlightOrder.isOrderFilled()).isFalse();
        }
    }

    private TradeUpdateEvent createFill(String tradeId, String price, String baseAmount, String quoteAmount, TokenAmount fee) {
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
