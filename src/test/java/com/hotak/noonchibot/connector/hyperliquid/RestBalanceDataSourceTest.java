package com.hotak.noonchibot.connector.hyperliquid;

import com.hotak.noonchibot.connector.web.testutils.RestClientTest;
import com.hotak.noonchibot.core.TestTaskScheduler;
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
        dataSource = new RestBalanceDataSource(restAssistant, eventPublisher, taskScheduler, HyperliquidFixture.USER);
    }

    @Test
    @DisplayName("clearinghouseState를 USDC balance snapshot으로 발행한다")
    void poll_publishesBalanceSnapshot() {
        runWith(HyperliquidFixture.clearinghouseStateSuccess(), () -> dataSource.poll());

        BalanceEvent.SnapshotReceived snapshot = eventPublisher.only(BalanceEvent.SnapshotReceived.class);
        assertThat(snapshot.assets()).containsOnlyKeys("USDC");
        assertThat(snapshot.assets().get("USDC").totalBalance()).isEqualByComparingTo(new BigDecimal("1000.0"));
        assertThat(snapshot.assets().get("USDC").availableBalance()).isEqualByComparingTo(new BigDecimal("950.0"));
    }

    @Test
    @DisplayName("시작 시 즉시 폴링하고 5초 주기 task를 등록한다")
    void onStart_pollsAndSchedules() {
        runWith(HyperliquidFixture.clearinghouseStateSuccess(), () -> dataSource.onStart());

        assertThat(eventPublisher.hasEventOfType(BalanceEvent.SnapshotReceived.class)).isTrue();
        assertThat(taskScheduler.onlyScheduledTask().period()).isEqualTo(Duration.ofSeconds(5));
    }
}
