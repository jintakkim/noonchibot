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
    private static final String CLIENT_ORDER_ID = "OID-123";
    private static final String EXCHANGE_ORDER_ID = "EX-1";
    private static final String TRADING_PAIR = "BTC-USDT";
    private static final Instant CREATED_AT = Instant.parse("2026-01-01T00:00:00Z");
    private static final Instant UPDATED_AT = Instant.parse("2026-01-01T00:00:05Z");

    private InFlightOrder inFlightOrder;

    @BeforeEach
    void setUp() {
        inFlightOrder = new InFlightOrder(
                CLIENT_ORDER_ID,
                TRADING_PAIR,
                OrderType.LIMIT,
                TradeType.BUY,
                new BigDecimal("1.0"),
                new BigDecimal("50000"),
                CREATED_AT,
                false,
                TimeInForce.GTC
        );
    }

    @Nested
    @DisplayName("상태 업데이트")
    class OrderUpdateTest {
        @Test
        @DisplayName("clientOrderId가 일치하면 exchangeOrderId와 상태가 갱신된다")
        void updatesByClientOrderId() {
            inFlightOrder.updateWithOrderUpdate(
                    CLIENT_ORDER_ID,
                    EXCHANGE_ORDER_ID,
                    OrderState.OPEN,
                    UPDATED_AT
            );

            assertThat(inFlightOrder.getCurrentState()).isEqualTo(OrderState.OPEN);
            assertThat(inFlightOrder.getExchangeOrderId()).isEqualTo(EXCHANGE_ORDER_ID);
            assertThat(inFlightOrder.getLastUpdateTimestamp()).isEqualTo(UPDATED_AT);
            assertThat(inFlightOrder.isDone()).isFalse();
        }

        @Test
        @DisplayName("exchangeOrderId가 일치하면 clientOrderId 없이도 상태가 갱신된다")
        void updatesByExchangeOrderId() {
            inFlightOrder.updateWithOrderUpdate(CLIENT_ORDER_ID, EXCHANGE_ORDER_ID, OrderState.OPEN, UPDATED_AT);

            inFlightOrder.updateWithOrderUpdate(
                    null,
                    EXCHANGE_ORDER_ID,
                    OrderState.PARTIALLY_FILLED,
                    UPDATED_AT.plusSeconds(1)
            );

            assertThat(inFlightOrder.getCurrentState()).isEqualTo(OrderState.PARTIALLY_FILLED);
            assertThat(inFlightOrder.getExchangeOrderId()).isEqualTo(EXCHANGE_ORDER_ID);
        }

        @Test
        @DisplayName("ID가 모두 일치하지 않으면 예외가 발생한다")
        void rejectsMismatchedIds() {
            assertThatThrownBy(() -> inFlightOrder.updateWithOrderUpdate(
                    "WRONG-ID",
                    "WRONG-EX",
                    OrderState.OPEN,
                    UPDATED_AT
            )).isInstanceOf(InFlightUpdateFailedException.class);
        }

        @Test
        @DisplayName("OPEN → PENDING_CANCEL → CANCELED 취소 흐름을 처리한다")
        void cancellationFlow() {
            inFlightOrder.updateWithOrderUpdate(CLIENT_ORDER_ID, EXCHANGE_ORDER_ID, OrderState.OPEN, UPDATED_AT);
            inFlightOrder.updateWithOrderUpdate(CLIENT_ORDER_ID, EXCHANGE_ORDER_ID, OrderState.PENDING_CANCEL, UPDATED_AT.plusSeconds(1));

            assertThat(inFlightOrder.isDone()).isFalse();

            inFlightOrder.updateWithOrderUpdate(CLIENT_ORDER_ID, EXCHANGE_ORDER_ID, OrderState.CANCELED, UPDATED_AT.plusSeconds(2));

            assertThat(inFlightOrder.getCurrentState()).isEqualTo(OrderState.CANCELED);
            assertThat(inFlightOrder.isDone()).isTrue();
        }

        @Test
        @DisplayName("허용되지 않은 상태 전이는 무시된다")
        void ignoresDisallowedTransition() {
            inFlightOrder.updateWithOrderUpdate(CLIENT_ORDER_ID, EXCHANGE_ORDER_ID, OrderState.FAILED, UPDATED_AT);

            assertThat(inFlightOrder.getCurrentState()).isEqualTo(OrderState.PENDING_CREATE);
            assertThat(inFlightOrder.getExchangeOrderId()).isEqualTo(EXCHANGE_ORDER_ID);
            assertThat(inFlightOrder.isDone()).isFalse();
        }

        @Test
        @DisplayName("부분 체결 후 FILLED 상태까지 처리한다")
        void partialToFullFill() {
            inFlightOrder.updateWithOrderUpdate(CLIENT_ORDER_ID, EXCHANGE_ORDER_ID, OrderState.OPEN, UPDATED_AT);
            applyTrade(fill("t-1", "0.5", "25000", null));
            inFlightOrder.updateWithOrderUpdate(CLIENT_ORDER_ID, EXCHANGE_ORDER_ID, OrderState.PARTIALLY_FILLED, UPDATED_AT.plusSeconds(1));

            assertThat(inFlightOrder.getCurrentState()).isEqualTo(OrderState.PARTIALLY_FILLED);
            assertThat(inFlightOrder.getExecutedAmountBase()).isEqualByComparingTo("0.5");
            assertThat(inFlightOrder.isDone()).isFalse();

            applyTrade(fill("t-2", "0.5", "25000", null));
            inFlightOrder.updateWithOrderUpdate(CLIENT_ORDER_ID, EXCHANGE_ORDER_ID, OrderState.FILLED, UPDATED_AT.plusSeconds(2));

            assertThat(inFlightOrder.getCurrentState()).isEqualTo(OrderState.FILLED);
            assertThat(inFlightOrder.getExecutedAmountBase()).isEqualByComparingTo("1.0");
            assertThat(inFlightOrder.isOrderFilled()).isTrue();
            assertThat(inFlightOrder.isDone()).isTrue();
        }
    }

    @Nested
    @DisplayName("체결 업데이트")
    class TradeUpdateTest {
        @Test
        @DisplayName("체결 수량과 금액이 누적되고 exchangeOrderId가 설정된다")
        void accumulatesFillsAndSetsExchangeOrderId() {
            applyTrade(fill("trade-1", "0.6", "30000", null));

            assertThat(inFlightOrder.getExchangeOrderId()).isEqualTo(EXCHANGE_ORDER_ID);
            assertThat(inFlightOrder.getExecutedAmountBase()).isEqualByComparingTo("0.6");
            assertThat(inFlightOrder.getExecutedAmountQuote()).isEqualByComparingTo("30000");
            assertThat(inFlightOrder.getProcessedTradeIds()).containsExactly("trade-1");
            assertThat(inFlightOrder.getLastUpdateTimestamp()).isEqualTo(UPDATED_AT);
        }

        @Test
        @DisplayName("exchangeOrderId가 일치하면 clientOrderId 없이도 체결 업데이트를 적용한다")
        void appliesTradeByExchangeOrderId() {
            inFlightOrder.updateWithOrderUpdate(CLIENT_ORDER_ID, EXCHANGE_ORDER_ID, OrderState.OPEN, UPDATED_AT);

            inFlightOrder.updateWithTradeUpdate(
                    "trade-1",
                    null,
                    EXCHANGE_ORDER_ID,
                    UPDATED_AT,
                    new BigDecimal("50000"),
                    new BigDecimal("0.6"),
                    new BigDecimal("30000"),
                    null,
                    true
            );

            assertThat(inFlightOrder.getExecutedAmountBase()).isEqualByComparingTo("0.6");
        }

        @Test
        @DisplayName("ID가 모두 일치하지 않으면 체결 업데이트를 거부한다")
        void rejectsMismatchedTradeIds() {
            assertThatThrownBy(() -> inFlightOrder.updateWithTradeUpdate(
                    "trade-1",
                    "WRONG-ID",
                    "WRONG-EX",
                    UPDATED_AT,
                    new BigDecimal("50000"),
                    new BigDecimal("0.6"),
                    new BigDecimal("30000"),
                    null,
                    true
            )).isInstanceOf(InFlightUpdateFailedException.class);
        }

        @Test
        @DisplayName("중복 tradeId는 체결량과 수수료를 중복 누적하지 않는다")
        void ignoresDuplicateTradeId() {
            applyTrade(fill("trade-1", "0.6", "30000", new TokenAmount("USDT", new BigDecimal("1.0"))));
            applyTrade(fill("trade-1", "0.6", "30000", new TokenAmount("USDT", new BigDecimal("1.0"))));

            assertThat(inFlightOrder.getExecutedAmountBase()).isEqualByComparingTo("0.6");
            assertThat(inFlightOrder.getExecutedAmountQuote()).isEqualByComparingTo("30000");
            assertThat(inFlightOrder.getAccumulatedFees()).containsEntry("USDT", new BigDecimal("1.0"));
        }
    }

    @Nested
    @DisplayName("평균 체결가")
    class AveragePriceTest {
        @Test
        @DisplayName("체결이 없으면 null을 반환한다")
        void returnsNullWhenNoFills() {
            assertThat(inFlightOrder.getAverageExecutedPrice()).isNull();
        }

        @Test
        @DisplayName("단일 체결 시 체결가와 동일하다")
        void singleFill() {
            applyTrade(fill("t-1", "1.0", "50000", null));

            assertThat(inFlightOrder.getAverageExecutedPrice()).isEqualByComparingTo("50000");
        }

        @Test
        @DisplayName("두 번 분할 체결 시 가중 평균을 계산한다")
        void weightedAverageWithTwoFills() {
            applyTrade(fill("t-1", "0.5", "25000.25", null));
            applyTrade(fill("t-2", "0.5", "25000.50", null));

            assertThat(inFlightOrder.getAverageExecutedPrice()).isEqualByComparingTo("50000.75");
        }
    }

    @Nested
    @DisplayName("수수료")
    class FeeTest {
        @Test
        @DisplayName("체결이 없으면 수수료는 비어있다")
        void emptyFeeWhenNoFills() {
            assertThat(inFlightOrder.getAccumulatedFees()).isEmpty();
        }

        @Test
        @DisplayName("같은 토큰 수수료를 합산한다")
        void accumulatesSameTokenFee() {
            applyTrade(fill("t-1", "0.5", "25000", new TokenAmount("USDT", new BigDecimal("25"))));
            applyTrade(fill("t-2", "0.5", "25000", new TokenAmount("USDT", new BigDecimal("25"))));

            assertThat(inFlightOrder.getAccumulatedFees()).containsEntry("USDT", new BigDecimal("50"));
        }

        @Test
        @DisplayName("수수료가 null이면 추가하지 않는다")
        void ignoresNullFee() {
            applyTrade(fill("t-1", "1.0", "50000", null));

            assertThat(inFlightOrder.getAccumulatedFees()).isEmpty();
        }
    }

    @Nested
    @DisplayName("체결 완료 판정")
    class FilledTest {
        @Test
        @DisplayName("체결량이 주문량과 같으면 체결 완료이다")
        void filledWhenAmountMatches() {
            applyTrade(fill("t-1", "1.0", "50000", null));

            assertThat(inFlightOrder.isOrderFilled()).isTrue();
            assertThat(inFlightOrder.isDone()).isTrue();
        }

        @Test
        @DisplayName("체결량이 허용 오차 이내이면 체결 완료로 판정한다")
        void filledWithinTolerance() {
            applyTrade(fill("t-1", "0.99999999", "49999.9995", null));

            assertThat(inFlightOrder.isOrderFilled()).isTrue();
        }

        @Test
        @DisplayName("체결량이 부족하면 미체결이다")
        void notFilledWhenShort() {
            applyTrade(fill("t-1", "0.5", "25000", null));

            assertThat(inFlightOrder.isOrderFilled()).isFalse();
            assertThat(inFlightOrder.isDone()).isFalse();
        }
    }

    @Test
    @DisplayName("OrderView로 현재 주문 상태를 노출한다")
    void toView() {
        inFlightOrder.updateWithOrderUpdate(CLIENT_ORDER_ID, EXCHANGE_ORDER_ID, OrderState.OPEN, UPDATED_AT);
        applyTrade(fill("t-1", "0.4", "20000", new TokenAmount("USDT", new BigDecimal("1.5"))));

        OrderView view = inFlightOrder.toView();

        assertThat(view.clientOrderId()).isEqualTo(CLIENT_ORDER_ID);
        assertThat(view.exchangeOrderId()).isEqualTo(EXCHANGE_ORDER_ID);
        assertThat(view.tradingPair()).isEqualTo(TRADING_PAIR);
        assertThat(view.state()).isEqualTo(OrderState.OPEN);
        assertThat(view.executedBaseAmount()).isEqualByComparingTo("0.4");
        assertThat(view.executedQuoteAmount()).isEqualByComparingTo("20000");
        assertThat(view.remainingBaseAmount()).isEqualByComparingTo("0.6");
        assertThat(view.processedTradeIds()).containsExactly("t-1");
        assertThat(view.accumulatedFees()).containsEntry("USDT", new BigDecimal("1.5"));
        assertThat(view.createdAt()).isEqualTo(CREATED_AT);
        assertThat(view.updatedAt()).isEqualTo(UPDATED_AT);
    }

    private TradeUpdate fill(String tradeId, String baseAmount, String quoteAmount, TokenAmount fee) {
        return new TradeUpdate(
                tradeId,
                CLIENT_ORDER_ID,
                EXCHANGE_ORDER_ID,
                UPDATED_AT,
                new BigDecimal("50000"),
                new BigDecimal(baseAmount),
                new BigDecimal(quoteAmount),
                fee,
                true
        );
    }

    private void applyTrade(TradeUpdate tradeUpdate) {
        inFlightOrder.updateWithTradeUpdate(
                tradeUpdate.tradeId(),
                tradeUpdate.clientOrderId(),
                tradeUpdate.exchangeOrderId(),
                tradeUpdate.fillTimestamp(),
                tradeUpdate.fillPrice(),
                tradeUpdate.fillBaseAmount(),
                tradeUpdate.fillQuoteAmount(),
                tradeUpdate.fee(),
                tradeUpdate.isMaker()
        );
    }

    private record TradeUpdate(
            String tradeId,
            String clientOrderId,
            String exchangeOrderId,
            Instant fillTimestamp,
            BigDecimal fillPrice,
            BigDecimal fillBaseAmount,
            BigDecimal fillQuoteAmount,
            TokenAmount fee,
            Boolean isMaker
    ) {
    }
}
