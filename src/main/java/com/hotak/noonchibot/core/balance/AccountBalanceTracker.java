package com.hotak.noonchibot.core.balance;

import com.hotak.noonchibot.core.event.BalanceSnapshotEvent;
import com.hotak.noonchibot.core.event.BalanceUpdateEvent;
import com.hotak.noonchibot.core.event.ExchangeEventSubscriber;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.StampedLock;


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
public class AccountBalanceTracker {
    public final String platformName;
    private final StampedLock lock = new StampedLock();
    private final Map<String, BigDecimal> accountBalances = new HashMap<>();
    private final Map<String, BigDecimal> accountAvailableBalances = new HashMap<>();
    /**
     * 외부 원천 서버에서 오는 타임스템프: 만약 원천 서버에서 타임스템프가 오지 않는다면 null이다.
     */
    private volatile Instant lastSnapshotTimestamp;
    /**
     * 외부 원천 서버에서 오는 타임스템프: 만약 원천 서버에서 타임스템프가 오지 않는다면 null이다.
     */
    private volatile Instant lastUpdateTimestamp;
    private final ExchangeEventSubscriber eventSubscriber;


    public void subscribeEvent() {
        eventSubscriber.subscribe(BalanceSnapshotEvent.class, this::processSnapshot);
        eventSubscriber.subscribe(BalanceUpdateEvent.class, this::processUpdate);
    }

    public BigDecimal getAvailableBalance(String currency) {
        long stamp = lock.tryOptimisticRead();
        BigDecimal value = accountAvailableBalances.get(currency);
        if (!lock.validate(stamp)) {
            stamp = lock.readLock();
            try {
                value = accountAvailableBalances.get(currency);
            } finally {
                lock.unlockRead(stamp);
            }
        }
        return value;
    }

    public BigDecimal getBalance(String currency) {
        long stamp = lock.tryOptimisticRead();
        BigDecimal value = accountBalances.get(currency);
        if (!lock.validate(stamp)) {
            stamp = lock.readLock();
            try {
                value = accountBalances.get(currency);
            } finally {
                lock.unlockRead(stamp);
            }
        }
        return value;
    }

    private void processSnapshot(BalanceSnapshotEvent event) {
        long stamp = lock.writeLock();
        try {
            if (lastUpdateTimestamp != null && event.timestamp() != null && event.timestamp().isBefore(lastUpdateTimestamp)) {
                log.warn("마지막 업데이트({})보다 오래된 스냅샷({})을 무시합니다", lastUpdateTimestamp, event.timestamp());
                return;
            }
            Set<String> localAssets = new HashSet<>(accountBalances.keySet());

            event.freeBalances().forEach((asset, free) -> {
                BigDecimal locked = event.lockedBalances().getOrDefault(asset, BigDecimal.ZERO);
                accountAvailableBalances.put(asset, free);
                accountBalances.put(asset, free.add(locked));
                localAssets.remove(asset);
            });

            // 거래소에 없는 자산 정리
            for (String asset : localAssets) {
                accountAvailableBalances.remove(asset);
                accountBalances.remove(asset);
            }
            lastSnapshotTimestamp = event.timestamp();
        } finally {
            lock.unlockWrite(stamp);
        }
    }

    private void processUpdate(BalanceUpdateEvent event) {
        long stamp = lock.writeLock();
        try {
            if (!isInitialized()) {
                log.warn("초기 스냅샷 적용 이전 상태입니다, 발생된 BalanceUpdateEvent를 무시합니다");
                return;
            }
            if (lastSnapshotTimestamp != null && event.timestamp() != null && event.timestamp().isBefore(lastSnapshotTimestamp)) {
                log.warn("마지막 스냅샷({})보다 오래된 업데이트({})를 무시합니다", lastSnapshotTimestamp, event.timestamp());
                return;
            }
            accountAvailableBalances.put(event.asset(), event.free());
            accountBalances.put(event.asset(), event.free().add(event.locked()));
            lastUpdateTimestamp = Instant.now();
        } finally {
            lock.unlockWrite(stamp);
        }
    }

    public boolean isInitialized() {
        return lastSnapshotTimestamp != null;
    }
}
