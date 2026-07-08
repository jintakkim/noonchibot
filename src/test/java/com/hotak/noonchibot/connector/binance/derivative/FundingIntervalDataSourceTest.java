package com.hotak.noonchibot.connector.binance.derivative;

import com.hotak.noonchibot.connector.web.testutils.RestClientTest;
import com.hotak.noonchibot.core.event.Event;
import com.hotak.noonchibot.core.event.EventHandler;
import com.hotak.noonchibot.core.event.EventSubscriber;
import com.hotak.noonchibot.core.event.ExecutionPolicy;
import com.hotak.noonchibot.core.event.Subscription;
import com.hotak.noonchibot.core.event.TestEventPublisher;
import com.hotak.noonchibot.core.event.internal.derivative.FundingInfoEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class FundingIntervalDataSourceTest extends RestClientTest {
    private TestEventPublisher eventPublisher;
    private FundingIntervalDataSource fundingIntervalDataSource;

    @BeforeEach
    void setup() {
        eventPublisher = new TestEventPublisher();
        fundingIntervalDataSource = new FundingIntervalDataSource(
                restAssistant,
                BinanceDerivativeFixture.BTC_ETH_SOL_REGISTRY,
                eventPublisher,
                new NoOpEventSubscriber()
        );
    }

    @Test
    @DisplayName("펀딩 인터벌 조회 성공시 IntervalReceived 이벤트 발행")
    void intervalFetchSuccessPublishesEvent() {
        runWith(
                BinanceDerivativeFixture.fundingInfoIntervalSuccess(Map.of(
                        "BTCUSDT", 4,
                        "ETHUSDT", 4,
                        "SOLUSDT", 1,
                        "SUSHIUSDT", 8
                )),
                () -> {
                    fundingIntervalDataSource.onEvent(new FundingInfoEvent.IntervalRestFetchRequested());
                    var occurred = eventPublisher.getFirstEventOfType(FundingInfoEvent.IntervalReceived.class);
                    assertThat(occurred)
                            .isPresent()
                            .hasValueSatisfying(event ->
                                    assertThat(event.snapshot())
                                            .containsEntry("BTC-USDT", Duration.ofHours(4))
                                            .containsEntry("ETH-USDT", Duration.ofHours(4))
                                            .containsEntry("SOL-USDT", Duration.ofHours(1))
                                            .doesNotContainKey("SUSHI-USDT"));
                }
        );
    }

    @Test
    @DisplayName("실패시 IntervalRestFetchFailed 이벤트 발행")
    void failureOccursFailedEvent() {
        fundingIntervalDataSource.onFailure(
                new FundingInfoEvent.IntervalRestFetchRequested(),
                new IllegalStateException("interval fetch failed")
        );
        assertThat(eventPublisher.hasEventOfType(FundingInfoEvent.IntervalRestFetchFailed.class)).isTrue();
    }

    private static class NoOpEventSubscriber implements EventSubscriber {
        @Override
        public <E extends Event> Subscription subscribe(
                Class<E> eventType,
                EventHandler<E> listener,
                ExecutionPolicy policy
        ) {
            return () -> {};
        }
    }
}
