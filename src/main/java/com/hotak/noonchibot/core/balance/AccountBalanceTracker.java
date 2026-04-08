package com.hotak.noonchibot.core.balance;

import com.google.common.annotations.VisibleForTesting;
import com.hotak.noonchibot.core.event.BalanceSnapshotEvent;
import com.hotak.noonchibot.core.event.BalanceUpdateEvent;
import com.hotak.noonchibot.core.event.EventListener;
import com.hotak.noonchibot.core.event.ExchangeEventSubscriber;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.SmartLifecycle;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * 초기 스냅샷 적용이 되어야 balanceTracker가 BalanceUpdateEvent을 적용한다.
 *
 * 자체 validating 적용: 네트워크 이슈등의 이유로 이전 요청이 이후 요청 이후에 응답이 왔을 경우를 대비하여 각 이벤트의 타임스템프를 비교한다, 가능한 경우의 수는 아래와 같다.
 * 1. 스냅샷 발생시 마지막 updateTimestamp보다 늦은 스냅샷이라면 해당 스냅샷은 무시된다.
 * 2. 업데이트 발생시 마지막 snapshotTimestamp보다 늦은 업데이트라면 해당 업데이트는 무시된다.
 * 3. 원천 서버가 타임스템프 미제공시 해당 validation은 적용되지 않는다.(반드시 적용)
 */
@Slf4j
@RequiredArgsConstructor
public class AccountBalanceTracker implements SmartLifecycle {
    public final String platformName;
    private final Map<String, BigDecimal> accountBalances = new HashMap<>();
    private final Map<String, BigDecimal> accountAvailableBalances = new HashMap<>();

    private final EventListener<BalanceSnapshotEvent> snapshotEventListener = this::processSnapshot;
    private final EventListener<BalanceUpdateEvent> updateEventListener = this::processUpdate;

    private Instant lastSnapshotTimestamp;
    private Instant lastUpdateTimestamp;
    private final ExchangeEventSubscriber eventSubscriber;

    public volatile boolean running = false;

    public BigDecimal getAvailableBalance(String currency) {
        return accountAvailableBalances.get(currency);
    }

    public BigDecimal getBalance(String currency) {
        return accountBalances.get(currency);
    }

    @VisibleForTesting
    void processSnapshot(BalanceSnapshotEvent event) {
        if (lastUpdateTimestamp != null && event.timestamp() != null
                && event.timestamp().isBefore(lastUpdateTimestamp)) {
            log.warn("마지막 업데이트({})보다 오래된 스냅샷({})을 무시합니다", lastUpdateTimestamp, event.timestamp());
            return;
        }
        Set<String> localAssets = new HashSet<>(accountBalances.keySet());

        event.totalBalances().forEach((asset, total) -> {
            accountBalances.put(asset, total);
            accountAvailableBalances.put(asset,
                    event.availableBalances().getOrDefault(asset, BigDecimal.ZERO));
            localAssets.remove(asset);
        });

        for (String asset : localAssets) {
            accountBalances.remove(asset);
            accountAvailableBalances.remove(asset);
        }
        lastSnapshotTimestamp = event.timestamp();
    }

    @VisibleForTesting
    void processUpdate(BalanceUpdateEvent event) {
        if (!isInitialized()) {
            log.warn("초기 스냅샷 적용 이전 상태입니다, 발생된 BalanceUpdateEvent를 무시합니다");
            return;
        }
        if (lastSnapshotTimestamp != null && event.timestamp() != null && event.timestamp().isBefore(lastSnapshotTimestamp)) {
            log.warn("마지막 스냅샷({})보다 오래된 업데이트({})를 무시합니다", lastSnapshotTimestamp, event.timestamp());
            return;
        }
        accountAvailableBalances.put(event.asset(), event.availableBalance());
        accountBalances.put(event.asset(), event.totalBalance());
        lastUpdateTimestamp = Instant.now();
    }

    public boolean isInitialized() {
        return lastSnapshotTimestamp != null;
    }

    @Override
    public void start() {
        eventSubscriber.subscribe(BalanceSnapshotEvent.class, snapshotEventListener);
        eventSubscriber.subscribe(BalanceUpdateEvent.class, updateEventListener);
        running = true;
    }

    @Override
    public void stop() {
        eventSubscriber.unsubscribe(BalanceSnapshotEvent.class, snapshotEventListener);
        eventSubscriber.unsubscribe(BalanceUpdateEvent.class, updateEventListener);
        running = false;
    }

    @Override
    public boolean isRunning() {
        return running;
    }
}
