package com.hotak.noonchibot.connector.binance.derivative;

import com.hotak.noonchibot.connector.web.testutils.RestClientTest;
import com.hotak.noonchibot.core.TestTaskScheduler;
import com.hotak.noonchibot.core.balance.AssetState;
import com.hotak.noonchibot.core.config.Phases;
import com.hotak.noonchibot.core.event.TestEventPublisher;
import com.hotak.noonchibot.core.event.internal.balance.BalanceEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class RestBalanceDataSourceTest extends RestClientTest {
    private TestEventPublisher eventPublisher;
    private TestTaskScheduler taskScheduler;
    private RestBalanceDataSource dataSource;

    @BeforeEach
    void setUp() {
        eventPublisher = new TestEventPublisher();
        taskScheduler = new TestTaskScheduler();
        dataSource = new RestBalanceDataSource(
                restAssistant,
                eventPublisher,
                taskScheduler
        );
    }

    @Test
    @DisplayName("폴링 성공시 SnapshotReceived 이벤트를 발행한다")
    void poll_publishesSnapshotEvent() {
        runWith(BinanceDerivativeFixture.accountSuccess(), () -> {
            dataSource.poll();
            assertThat(eventPublisher.hasEventOfType(BalanceEvent.SnapshotReceived.class)).isTrue();
        });
    }

    @Test
    @DisplayName("응답의 모든 자산을 파싱한다 (0 잔고 포함)")
    void poll_parsesAllAssetsIncludingZero() {
        runWith(BinanceDerivativeFixture.accountSuccess(), () -> {
            dataSource.poll();

            var snapshot = eventPublisher.getFirstEventOfType(BalanceEvent.SnapshotReceived.class);
            assertThat(snapshot).isPresent().hasValueSatisfying(event ->
                    assertThat(event.assets()).containsOnlyKeys(
                            "FDUSD", "BNB", "ETH", "BTC", "USDT", "USDC"));
        });
    }

    @Test
    @DisplayName("USDT 자산의 marginBalance/availableBalance/timestamp를 정확히 매핑한다")
    void poll_mapsUsdtCorrectly() {
        runWith(BinanceDerivativeFixture.accountSuccess(), () -> {
            dataSource.poll();

            var snapshot = eventPublisher.getFirstEventOfType(BalanceEvent.SnapshotReceived.class);
            assertThat(snapshot).isPresent().hasValueSatisfying(event -> {
                AssetState usdt = event.assets().get("USDT");
                assertThat(usdt.totalBalance()).isEqualByComparingTo(new BigDecimal("5000"));
                assertThat(usdt.availableBalance()).isEqualByComparingTo(new BigDecimal("5000"));
                assertThat(usdt.timestamp()).isEqualTo(Instant.ofEpochMilli(1778546240488L));
            });
        });
    }

    @Test
    @DisplayName("시작 시 즉시 1회 폴링하여 스냅샷을 발행한다")
    void onStart_pollsImmediately() {
        runWith(BinanceDerivativeFixture.accountSuccess(), () -> {
            dataSource.onStart();

            assertThat(eventPublisher.hasEventOfType(BalanceEvent.SnapshotReceived.class))
                    .isTrue();
        });
    }

    @Test
    @DisplayName("시작 시 TaskScheduler에 5초 주기 폴링 task를 등록한다")
    void onStart_registersPollingTask() {
        runWith(BinanceDerivativeFixture.accountSuccess(), () -> {
            dataSource.onStart();
            assertThat(taskScheduler.onlyScheduledTask().period()).isEqualTo(Duration.ofSeconds(5));
        });
    }

    @Test
    @DisplayName("종료 시 등록된 task를 취소한다")
    void onShutdown_cancelsTask() {
        runWith(BinanceDerivativeFixture.accountSuccess(), () -> {
            dataSource.onStart();
            dataSource.onShutdown();
            assertThat(taskScheduler.onlyScheduledTask().isCancelled()).isTrue();
        });
    }

    @Test
    @DisplayName("phase는 BALANCE_SETUP이다")
    void phaseIsBalanceSetup() {
        assertThat(dataSource.phase()).isEqualTo(Phases.BALANCE_SETUP);
    }

}
