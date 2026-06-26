package com.hotak.noonchibot.core.balance;

import com.hotak.noonchibot.core.event.ExecutionPolicy;
import com.hotak.noonchibot.core.event.TestEventSubscriber;
import com.hotak.noonchibot.core.event.internal.balance.BalanceEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AccountBalanceTrackerTest {
    private static final Instant SNAPSHOT_TIME = Instant.parse("2026-06-24T00:00:00Z");
    private static final Instant UPDATE_TIME = Instant.parse("2026-06-24T00:00:10Z");

    private TestEventSubscriber eventSubscriber;
    private AccountBalanceTracker tracker;

    @BeforeEach
    void setUp() {
        eventSubscriber = new TestEventSubscriber();
        tracker = new AccountBalanceTracker(eventSubscriber);
    }

    @Test
    @DisplayName("스냅샷 수신 시 전체 잔고와 사용 가능 잔고를 초기화한다")
    void processSnapshot_initializesBalances() {
        tracker.processSnapshot(snapshot(Map.of(
                "USDT", asset("100.0", "80.0"),
                "BTC", asset("0.5", "0.4")
        ), SNAPSHOT_TIME));

        assertThat(tracker.isInitialized()).isTrue();
        assertThat(tracker.getBalance("USDT")).isEqualByComparingTo("100.0");
        assertThat(tracker.getAvailableBalance("USDT")).isEqualByComparingTo("80.0");
        assertThat(tracker.getBalance("BTC")).isEqualByComparingTo("0.5");
        assertThat(tracker.getAvailableBalance("BTC")).isEqualByComparingTo("0.4");
    }

    @Test
    @DisplayName("새 스냅샷에 없는 기존 자산은 제거한다")
    void processSnapshot_removesAssetsMissingFromSnapshot() {
        tracker.processSnapshot(snapshot(Map.of(
                "USDT", asset("100.0", "80.0"),
                "BTC", asset("0.5", "0.4")
        ), SNAPSHOT_TIME));

        tracker.processSnapshot(snapshot(Map.of(
                "USDT", asset("120.0", "90.0")
        ), SNAPSHOT_TIME.plusSeconds(10)));

        assertThat(tracker.getBalance("USDT")).isEqualByComparingTo("120.0");
        assertThat(tracker.getAvailableBalance("USDT")).isEqualByComparingTo("90.0");
        assertThat(tracker.getBalance("BTC")).isNull();
        assertThat(tracker.getAvailableBalance("BTC")).isNull();
    }

    @Test
    @DisplayName("초기 스냅샷 전 업데이트는 무시한다")
    void processUpdate_beforeSnapshot_ignoresUpdate() {
        tracker.processUpdate(update(Map.of(
                "USDT", asset("100.0", "80.0")
        ), UPDATE_TIME));

        assertThat(tracker.isInitialized()).isFalse();
        assertThat(tracker.getBalance("USDT")).isNull();
        assertThat(tracker.getAvailableBalance("USDT")).isNull();
    }

    @Test
    @DisplayName("초기화 이후 업데이트는 해당 자산만 반영한다")
    void processUpdate_afterSnapshot_updatesOnlyProvidedAssets() {
        tracker.processSnapshot(snapshot(Map.of(
                "USDT", asset("100.0", "80.0"),
                "BTC", asset("0.5", "0.4")
        ), SNAPSHOT_TIME));

        tracker.processUpdate(update(Map.of(
                "USDT", asset("150.0", "130.0")
        ), UPDATE_TIME));

        assertThat(tracker.getBalance("USDT")).isEqualByComparingTo("150.0");
        assertThat(tracker.getAvailableBalance("USDT")).isEqualByComparingTo("130.0");
        assertThat(tracker.getBalance("BTC")).isEqualByComparingTo("0.5");
        assertThat(tracker.getAvailableBalance("BTC")).isEqualByComparingTo("0.4");
    }

    @Test
    @DisplayName("마지막 스냅샷보다 오래된 업데이트는 무시한다")
    void processUpdate_olderThanSnapshot_ignoresUpdate() {
        tracker.processSnapshot(snapshot(Map.of(
                "USDT", asset("100.0", "80.0")
        ), SNAPSHOT_TIME));

        tracker.processUpdate(update(Map.of(
                "USDT", asset("150.0", "130.0")
        ), SNAPSHOT_TIME.minusSeconds(1)));

        assertThat(tracker.getBalance("USDT")).isEqualByComparingTo("100.0");
        assertThat(tracker.getAvailableBalance("USDT")).isEqualByComparingTo("80.0");
    }

    @Test
    @DisplayName("마지막 업데이트보다 오래된 스냅샷은 무시한다")
    void processSnapshot_olderThanUpdate_ignoresSnapshot() {
        tracker.processSnapshot(snapshot(Map.of(
                "USDT", asset("100.0", "80.0")
        ), SNAPSHOT_TIME));
        tracker.processUpdate(update(Map.of(
                "USDT", asset("150.0", "130.0")
        ), UPDATE_TIME));

        tracker.processSnapshot(snapshot(Map.of(
                "USDT", asset("90.0", "70.0")
        ), UPDATE_TIME.minusSeconds(1)));

        assertThat(tracker.getBalance("USDT")).isEqualByComparingTo("150.0");
        assertThat(tracker.getAvailableBalance("USDT")).isEqualByComparingTo("130.0");
    }

    @Test
    @DisplayName("시작 시 스냅샷과 업데이트 이벤트를 sequential policy로 구독한다")
    void onStart_subscribesBalanceEvents() {
        tracker.onStart();

        assertThat(eventSubscriber.count()).isEqualTo(2);
        assertThat(eventSubscriber.countFor(BalanceEvent.SnapshotReceived.class)).isEqualTo(1);
        assertThat(eventSubscriber.countFor(BalanceEvent.UpdateReceived.class)).isEqualTo(1);
        assertThat(eventSubscriber.getSubscriptionsFor(BalanceEvent.SnapshotReceived.class).getFirst().policy())
                .isInstanceOf(ExecutionPolicy.Sequential.class);
        assertThat(eventSubscriber.getSubscriptionsFor(BalanceEvent.UpdateReceived.class).getFirst().policy())
                .isInstanceOf(ExecutionPolicy.Sequential.class);
    }

    @Test
    @DisplayName("구독된 핸들러는 이벤트를 tracker 상태에 반영한다")
    void subscribedHandlers_processEvents() {
        tracker.onStart();

        eventSubscriber.getSubscriptionsFor(BalanceEvent.SnapshotReceived.class)
                .getFirst()
                .handler()
                .onEvent(snapshot(Map.of("USDT", asset("100.0", "80.0")), SNAPSHOT_TIME));
        eventSubscriber.getSubscriptionsFor(BalanceEvent.UpdateReceived.class)
                .getFirst()
                .handler()
                .onEvent(update(Map.of("USDT", asset("120.0", "90.0")), UPDATE_TIME));

        assertThat(tracker.getBalance("USDT")).isEqualByComparingTo("120.0");
        assertThat(tracker.getAvailableBalance("USDT")).isEqualByComparingTo("90.0");
    }

    @Test
    @DisplayName("종료 시 등록된 구독을 모두 해제한다")
    void onShutdown_closesSubscriptions() {
        tracker.onStart();

        tracker.onShutdown();

        assertThat(eventSubscriber.count()).isZero();
    }

    private BalanceEvent.SnapshotReceived snapshot(Map<String, AssetState> assets, Instant timestamp) {
        return new BalanceEvent.SnapshotReceived(assets, timestamp);
    }

    private BalanceEvent.UpdateReceived update(Map<String, AssetState> assets, Instant timestamp) {
        return new BalanceEvent.UpdateReceived(assets, timestamp);
    }

    private AssetState asset(String totalBalance, String availableBalance) {
        return new AssetState(
                new BigDecimal(totalBalance),
                new BigDecimal(availableBalance),
                SNAPSHOT_TIME
        );
    }
}
