package com.hotak.noonchibot.core.derivative;

import com.google.common.annotations.VisibleForTesting;
import com.hotak.noonchibot.core.LifecycleAware;
import com.hotak.noonchibot.core.config.Phases;
import com.hotak.noonchibot.core.derivative.funding.FundingPayment;
import com.hotak.noonchibot.core.event.EventSubscriber;
import com.hotak.noonchibot.core.event.ExecutionPolicy;
import com.hotak.noonchibot.core.event.Subscription;
import com.hotak.noonchibot.core.event.internal.derivative.PositionEvent;
import lombok.RequiredArgsConstructor;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.HashSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@RequiredArgsConstructor
public class PositionTracker implements LifecycleAware {
    private final Map<String, Position> positions = new HashMap<>();
    private final EventSubscriber eventSubscriber;
    private final Set<Subscription> subscriptions = new HashSet<>();


    public Optional<Position> findPosition(String tradingPair, PositionSide positionSide) {
        String key = createPositionKey(tradingPair, positionSide);
        return Optional.ofNullable(positions.get(key));
    }

    private String createPositionKey(String tradingPair, PositionSide positionSide) {
        return tradingPair + "_" + positionSide.name();
    }

    @VisibleForTesting
    void onPositionUpdated(PositionEvent.UpdateReceived event) {
        String key = createPositionKey(event.tradingPair(), event.positionSide());
        if (event.amount().compareTo(BigDecimal.ZERO) == 0) {
            // 포지션 종료
            positions.remove(key);
            return;
        }
        Position position = positions.get(key);
        if (position == null) {
            positions.put(key, new Position(
                    event.tradingPair(),
                    event.positionSide(),
                    event.timestamp(),
                    event.unrealizedPnl(),
                    event.entryPrice(),
                    event.amount()
            ));
            return;
        }
        position.update(event.unrealizedPnl(), event.entryPrice(), event.amount());
    }

    public Collection<Position> getPositions() {
        return positions.values();
    }

    public void applyFundingPayment(FundingPayment payment) {
        String key = createPositionKey(payment.tradingPair(), payment.positionSide());
        Position position = positions.get(key);
        if (position == null) return;
        position.applyFundingPayment(payment.amount());
    }

    @Override
    public void onStart() {
        subscriptions.add(eventSubscriber.subscribe(
                PositionEvent.UpdateReceived.class,
                this::onPositionUpdated,
                ExecutionPolicy.sequential()
        ));
    }

    @Override
    public void onShutdown() {
        subscriptions.forEach(Subscription::close);
        subscriptions.clear();
    }

    @Override
    public int phase() {
        return Phases.POSITION_TRACKER_SETUP;
    }
}
