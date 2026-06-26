package com.hotak.noonchibot.core.derivative;

import com.hotak.noonchibot.connector.SimpleTradingPairSymbolRegistry;
import com.hotak.noonchibot.core.event.TestEventSubscriber;
import com.hotak.noonchibot.core.event.internal.derivative.FundingInfoEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalUnit;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class FundingInfoTrackerTest {
    private static final Duration DEFAULT_INTERVAL = Duration.ofHours(8);
    private static final String FUNDING_COIN = "USDT";
    private static final String BTC_PAIR = "BTC-USDT";
    private static final String ETH_PAIR = "ETH-USDT";
    private static final Instant EVENT_TIME = Instant.parse("2026-01-01T00:00:00Z");
    private static final Instant NEXT_FUNDING_TIME = Instant.parse("2026-01-01T08:00:00Z");

    private FundingInfoTracker tracker;
    private TestEventSubscriber eventSubscriber;

    @BeforeEach
    void setUp() {
        eventSubscriber = new TestEventSubscriber();
        tracker = new FundingInfoTracker(
                DEFAULT_INTERVAL,
                FUNDING_COIN,
                new SimpleTradingPairSymbolRegistry(Map.of(
                        BTC_PAIR, "BTCUSDT",
                        ETH_PAIR, "ETHUSDT"
                )),
                eventSubscriber
        );
    }

    @Nested
    @DisplayName("라이프사이클")
    class LifecycleTest {
        @Test
        @DisplayName("onStart 시 tradingPair를 등록하고 funding info 이벤트를 구독한다")
        void onStartRegistersPairsAndSubscribesEvents() {
            tracker.onStart();

            assertThat(eventSubscriber.isSubscribed(FundingInfoEvent.Received.class)).isTrue();
            assertThat(eventSubscriber.isSubscribed(FundingInfoEvent.IntervalReceived.class)).isTrue();
            assertThat(tracker.getFundingInfo(BTC_PAIR)).isNull();
            assertThat(tracker.getFundingInfo(ETH_PAIR)).isNull();
        }

        @Test
        @DisplayName("onShutdown 시 구독을 해제한다")
        void onShutdownClosesSubscriptions() {
            tracker.onStart();

            tracker.onShutdown();

            assertThat(eventSubscriber.count()).isZero();
        }
    }

    @Nested
    @DisplayName("FundingInfoEvent.Received 처리")
    class ReceivedTest {
        @Test
        @DisplayName("등록된 tradingPair의 funding info를 업데이트한다")
        void updatesRegisteredPair() {
            register(BTC_PAIR);

            tracker.processMessage(received(BTC_PAIR, "50000", "0.0001", NEXT_FUNDING_TIME, Duration.ofHours(8)));

            FundingInfo info = tracker.getFundingInfo(BTC_PAIR);
            assertThat(info).isNotNull();
            assertThat(info.getTradingPair()).isEqualTo(BTC_PAIR);
            assertThat(info.getFundingCoin()).isEqualTo(FUNDING_COIN);
            assertThat(info.getMarkPrice()).isEqualByComparingTo("50000");
            assertThat(info.getFundingRate()).isEqualByComparingTo("0.0001");
            assertThat(info.getNextFundingTime()).isEqualTo(NEXT_FUNDING_TIME);
            assertThat(info.getFundingInterval()).isEqualTo(Duration.ofHours(8));
        }

        @Test
        @DisplayName("fundingInterval이 null이면 기존 기본 interval을 유지한다")
        void keepsDefaultIntervalWhenEventIntervalIsNull() {
            register(BTC_PAIR);

            tracker.processMessage(received(BTC_PAIR, "50000", "0.0001", NEXT_FUNDING_TIME, null));

            FundingInfo info = tracker.getFundingInfo(BTC_PAIR);
            assertThat(info).isNotNull();
            assertThat(info.getFundingInterval()).isEqualTo(DEFAULT_INTERVAL);
        }

        @Test
        @DisplayName("동일 tradingPair는 최신 값으로 덮어쓴다")
        void overwritesWithLatestValue() {
            register(BTC_PAIR);

            tracker.processMessage(received(BTC_PAIR, "50000", "0.0001", NEXT_FUNDING_TIME, Duration.ofHours(8)));
            tracker.processMessage(received(BTC_PAIR, "51000", "0.0002", NEXT_FUNDING_TIME.plus(8, ChronoUnit.HOURS), Duration.ofHours(4)));

            FundingInfo info = tracker.getFundingInfo(BTC_PAIR);
            assertThat(info.getMarkPrice()).isEqualByComparingTo("51000");
            assertThat(info.getFundingRate()).isEqualByComparingTo("0.0002");
            assertThat(info.getNextFundingTime()).isEqualTo(NEXT_FUNDING_TIME.plus(8, ChronoUnit.HOURS));
            assertThat(info.getFundingInterval()).isEqualTo(Duration.ofHours(4));
        }

        @Test
        @DisplayName("등록되지 않은 tradingPair의 메시지는 무시한다")
        void ignoresUnregisteredPair() {
            register(BTC_PAIR);

            tracker.processMessage(received(ETH_PAIR, "3000", "0.0002", NEXT_FUNDING_TIME, Duration.ofHours(8)));

            assertThat(tracker.getFundingInfo(ETH_PAIR)).isNull();
            assertThat(tracker.getFundingInfo(BTC_PAIR)).isNull();
        }
    }

    @Nested
    @DisplayName("FundingInfoEvent.IntervalReceived 처리")
    class IntervalReceivedTest {
        @Test
        @DisplayName("등록된 tradingPair의 interval을 업데이트한다")
        void updatesRegisteredPairInterval() {
            register(BTC_PAIR);

            tracker.processMessage(received(BTC_PAIR, "50000", "0.0001", NEXT_FUNDING_TIME, null));
            tracker.processIntervalMessage(new FundingInfoEvent.IntervalReceived(Map.of(BTC_PAIR, Duration.ofHours(4))));

            FundingInfo info = tracker.getFundingInfo(BTC_PAIR);
            assertThat(info.getFundingInterval()).isEqualTo(Duration.ofHours(4));
        }

        @Test
        @DisplayName("등록되지 않은 tradingPair의 interval은 무시한다")
        void ignoresUnregisteredPairInterval() {
            register(BTC_PAIR);

            tracker.processIntervalMessage(new FundingInfoEvent.IntervalReceived(Map.of(ETH_PAIR, Duration.ofHours(4))));

            assertThat(tracker.getFundingInfo(ETH_PAIR)).isNull();
            assertThat(tracker.getFundingInfo(BTC_PAIR)).isNull();
        }
    }

    @Nested
    @DisplayName("조회")
    class QueryTest {
        @Test
        @DisplayName("등록되지 않은 pair는 null을 반환한다")
        void returnsNullForUnregisteredPair() {
            register(BTC_PAIR);

            assertThat(tracker.getFundingInfo(ETH_PAIR)).isNull();
        }

        @Test
        @DisplayName("초기화 전 pair는 null을 반환한다")
        void returnsNullBeforeInitialized() {
            register(BTC_PAIR);

            assertThat(tracker.getFundingInfo(BTC_PAIR)).isNull();
        }

        @Test
        @DisplayName("필수 값이 모두 들어오면 FundingInfo를 반환한다")
        void returnsFundingInfoAfterInitialized() {
            register(BTC_PAIR);

            tracker.processMessage(received(BTC_PAIR, "50000", "0.0001", NEXT_FUNDING_TIME, null));

            assertThat(tracker.getFundingInfo(BTC_PAIR)).isNotNull();
        }
    }

    private void register(String tradingPair) {
        tracker.registerTradingPairs(Set.of(tradingPair));
    }

    private FundingInfoEvent.Received received(
            String tradingPair,
            String markPrice,
            String fundingRate,
            Instant nextFundingTime,
            Duration interval
    ) {
        return new FundingInfoEvent.Received(
                tradingPair,
                EVENT_TIME,
                new BigDecimal(markPrice),
                new BigDecimal(fundingRate),
                nextFundingTime,
                interval
        );
    }
}
