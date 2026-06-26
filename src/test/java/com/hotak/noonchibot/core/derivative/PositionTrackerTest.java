package com.hotak.noonchibot.core.derivative;

import com.hotak.noonchibot.core.Exchange;
import com.hotak.noonchibot.core.event.TestEventSubscriber;
import com.hotak.noonchibot.core.event.internal.derivative.PositionEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class PositionTrackerTest {
    private PositionTracker tracker;
    private TestEventSubscriber eventSubscriber;

    @BeforeEach
    void setUp() {
        eventSubscriber = new TestEventSubscriber();
        tracker = new PositionTracker(eventSubscriber);
    }

    @Test
    @DisplayName("onStart 시 position update 이벤트를 구독한다")
    void onStartSubscribesPositionUpdate() {
        tracker.onStart();

        assertThat(eventSubscriber.isSubscribed(PositionEvent.UpdateReceived.class)).isTrue();
    }

    @Test
    @DisplayName("신규 포지션 이벤트 수신 시 새 Position이 저장된다")
    void createsNewPosition() {
        Instant openedAt = Instant.parse("2026-01-01T00:00:00Z");

        tracker.onPositionUpdated(positionUpdate("0.5", "45000", "100", openedAt));

        Position position = tracker.findPosition("BTC-USDT", PositionSide.LONG).orElseThrow();
        assertThat(position.getTradingPair()).isEqualTo("BTC-USDT");
        assertThat(position.getPositionSide()).isEqualTo(PositionSide.LONG);
        assertThat(position.getOpenedAt()).isEqualTo(openedAt);
        assertThat(position.getAmount()).isEqualByComparingTo("0.5");
        assertThat(position.getEntryPrice()).isEqualByComparingTo("45000");
        assertThat(position.getUnrealizedPnl()).isEqualByComparingTo("100");
    }

    @Test
    @DisplayName("기존 포지션 이벤트 수신 시 값이 업데이트되고 openedAt은 유지된다")
    void updatesExistingPosition() {
        Instant openedAt = Instant.parse("2026-01-01T00:00:00Z");
        tracker.onPositionUpdated(positionUpdate("0.5", "45000", "100", openedAt));

        tracker.onPositionUpdated(positionUpdate("1.0", "46000", "200", openedAt.plusSeconds(60)));

        Position position = tracker.findPosition("BTC-USDT", PositionSide.LONG).orElseThrow();
        assertThat(position.getOpenedAt()).isEqualTo(openedAt);
        assertThat(position.getAmount()).isEqualByComparingTo("1.0");
        assertThat(position.getEntryPrice()).isEqualByComparingTo("46000");
        assertThat(position.getUnrealizedPnl()).isEqualByComparingTo("200");
    }

    @Test
    @DisplayName("amount가 0인 이벤트 수신 시 포지션이 제거된다")
    void removesPositionOnZeroAmount() {
        tracker.onPositionUpdated(positionUpdate("0.5", "45000", "100", Instant.now()));

        tracker.onPositionUpdated(positionUpdate("0", "0", "0", Instant.now()));

        assertThat(tracker.findPosition("BTC-USDT", PositionSide.LONG)).isEmpty();
    }

    @Test
    @DisplayName("열린 포지션에 funding payment를 누적한다")
    void appliesFundingPaymentToOpenPosition() {
        tracker.onPositionUpdated(positionUpdate("0.5", "45000", "100", Instant.now()));

        tracker.applyFundingPayment(new FundingPayment(
                "funding-1",
                Exchange.BINANCE_DERIVATIVE,
                "BTC-USDT",
                PositionSide.LONG,
                new BigDecimal("-1.25"),
                "USDT",
                Instant.now()
        ));

        Position position = tracker.findPosition("BTC-USDT", PositionSide.LONG).orElseThrow();
        assertThat(position.getAccumulatedFundingPayment()).isEqualByComparingTo("-1.25");
        assertThat(position.netUnrealizedPnl()).isEqualByComparingTo("98.75");
    }

    @Test
    @DisplayName("존재하지 않는 포지션에 대한 funding payment는 무시한다")
    void ignoresFundingPaymentWithoutOpenPosition() {
        assertThatCode(() -> tracker.applyFundingPayment(new FundingPayment(
                "funding-1",
                Exchange.BINANCE_DERIVATIVE,
                "BTC-USDT",
                PositionSide.LONG,
                new BigDecimal("-1.25"),
                "USDT",
                Instant.now()
        ))).doesNotThrowAnyException();
    }

    private PositionEvent.UpdateReceived positionUpdate(
            String amount,
            String entryPrice,
            String unrealizedPnl,
            Instant timestamp
    ) {
        return new PositionEvent.UpdateReceived(
                "BTC-USDT",
                PositionSide.LONG,
                new BigDecimal(amount),
                new BigDecimal(entryPrice),
                new BigDecimal(unrealizedPnl),
                timestamp
        );
    }
}
