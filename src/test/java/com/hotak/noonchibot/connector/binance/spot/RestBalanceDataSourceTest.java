package com.hotak.noonchibot.connector.binance.spot;

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

import static org.assertj.core.api.Assertions.assertThat;

class RestBalanceDataSourceTest extends RestClientTest {
    private TestEventPublisher eventPublisher;
    private TestTaskScheduler taskScheduler;
    private RestBalanceDataSource dataSource;

    @BeforeEach
    void setUp() {
        eventPublisher = new TestEventPublisher();
        taskScheduler = new TestTaskScheduler();
        dataSource = new RestBalanceDataSource(restAssistant, eventPublisher, taskScheduler);
    }

    @Test
    @DisplayName("폴링 성공 시 spot account snapshot 이벤트를 발행한다")
    void poll_publishesSnapshotEvent() {
        runWith(BinanceSpotFixture.accountSuccess(), () -> dataSource.poll());

        BalanceEvent.SnapshotReceived snapshot = eventPublisher.only(BalanceEvent.SnapshotReceived.class);
        assertThat(snapshot.assets()).containsOnlyKeys("BTC", "USDT");
        assertThat(snapshot.timestamp()).isEqualTo(BinanceSpotFixture.ACCOUNT_TIME);
        assertThat(snapshot.assets().get("BTC")).isEqualTo(new AssetState(
                new BigDecimal("0.12"),
                new BigDecimal("0.10"),
                BinanceSpotFixture.ACCOUNT_TIME
        ));
    }

    @Test
    @DisplayName("시작 시 즉시 폴링하고 5초 주기 task를 등록한다")
    void onStart_pollsAndRegistersTask() {
        runWith(BinanceSpotFixture.accountSuccess(), () -> dataSource.onStart());

        assertThat(eventPublisher.hasEventOfType(BalanceEvent.SnapshotReceived.class)).isTrue();
        assertThat(taskScheduler.onlyScheduledTask().period()).isEqualTo(Duration.ofSeconds(5));
    }

    @Test
    @DisplayName("종료 시 등록된 task를 취소한다")
    void onShutdown_cancelsTask() {
        runWith(BinanceSpotFixture.accountSuccess(), () -> dataSource.onStart());

        dataSource.onShutdown();

        assertThat(taskScheduler.onlyScheduledTask().isCancelled()).isTrue();
    }

    @Test
    @DisplayName("phase는 BALANCE_SETUP이다")
    void phaseIsBalanceSetup() {
        assertThat(dataSource.phase()).isEqualTo(Phases.BALANCE_SETUP);
    }
}
