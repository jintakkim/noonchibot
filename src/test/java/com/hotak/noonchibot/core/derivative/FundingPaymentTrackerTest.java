package com.hotak.noonchibot.core.derivative;

import com.hotak.noonchibot.core.Exchange;
import com.hotak.noonchibot.core.event.TestEventPublisher;
import com.hotak.noonchibot.core.event.TestEventSubscriber;
import com.hotak.noonchibot.core.event.internal.derivative.FundingPaymentEvent;
import com.hotak.noonchibot.core.event.internal.derivative.PositionEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class FundingPaymentTrackerTest {
    private TestEventPublisher eventPublisher;
    private FundingPaymentRepository repository;
    private PositionTracker positionTracker;
    private FundingPaymentTracker tracker;
    private TestEventSubscriber eventSubscriber;

    @BeforeEach
    void setUp() {
        eventPublisher = new TestEventPublisher();
        repository = mock(FundingPaymentRepository.class);
        positionTracker = new PositionTracker(new TestEventSubscriber());
        eventSubscriber = new TestEventSubscriber();
        tracker = new FundingPaymentTracker(positionTracker, eventPublisher, eventSubscriber);
    }

    @Test
    @DisplayName("onStart 시 funding payment received 이벤트를 구독한다")
    void onStartSubscribesFundingPaymentReceived() {
        tracker.onStart();

        assertThat(eventSubscriber.isSubscribed(FundingPaymentEvent.Received.class)).isTrue();
    }

    @Test
    @DisplayName("funding payment를 열린 포지션에 누적하고 snapshot update 요청을 발행한다")
    void processFundingPayment_appliesToOpenPositionAndPublishesSnapshotUpdateRequested() {
        positionTracker.onPositionUpdated(new PositionEvent.UpdateReceived(
                "BTC-USDT",
                PositionSide.LONG,
                new BigDecimal("0.5"),
                new BigDecimal("45000"),
                new BigDecimal("100"),
                Instant.parse("2026-01-01T00:00:00Z")
        ));
        FundingPayment payment = new FundingPayment(
                "funding-1",
                Exchange.BINANCE_DERIVATIVE,
                "BTC-USDT",
                PositionSide.LONG,
                new BigDecimal("-1.25"),
                "USDT",
                Instant.parse("2026-01-01T08:00:00Z")
        );

        tracker.processFundingPayment(new FundingPaymentEvent.Received(payment));

        Position position = positionTracker.findPosition("BTC-USDT", PositionSide.LONG).orElseThrow();
        assertThat(position.getAccumulatedFundingPayment()).isEqualByComparingTo("-1.25");
        assertThat(eventPublisher.only(FundingPaymentEvent.SnapshotUpdateRequested.class).payment()).isEqualTo(payment);
    }

    @Test
    @DisplayName("snapshot updater는 funding payment history를 저장한다")
    void snapshotUpdater_savesFundingPaymentHistory() {
        FundingPaymentSnapshotUpdater updater = new FundingPaymentSnapshotUpdater(repository, eventSubscriber);
        FundingPayment payment = new FundingPayment(
                "funding-1",
                Exchange.BINANCE_DERIVATIVE,
                "BTC-USDT",
                PositionSide.LONG,
                new BigDecimal("-1.25"),
                "USDT",
                Instant.parse("2026-01-01T08:00:00Z")
        );

        updater.onEvent(new FundingPaymentEvent.SnapshotUpdateRequested(payment));

        verify(repository).save(any(FundingPaymentHistory.class));
    }
}
