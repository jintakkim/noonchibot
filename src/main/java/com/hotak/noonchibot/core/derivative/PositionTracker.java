package com.hotak.noonchibot.core.derivative;

import com.google.common.annotations.VisibleForTesting;
import com.hotak.noonchibot.core.LifecycleAware;
import com.hotak.noonchibot.core.event.PositionUpdateEvent;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class PositionTracker implements LifecycleAware {
    private final Map<String, Position> positions = new HashMap<>();


    public Optional<Position> findPosition(String tradingPair, PositionSide positionSide) {
        String key = createPositionKey(tradingPair, positionSide);
        return Optional.ofNullable(positions.get(key));
    }

    private String createPositionKey(String tradingPair, PositionSide positionSide) {
        return tradingPair + "_" + positionSide.name();
    }

    @VisibleForTesting
    void onPositionUpdated(PositionUpdateEvent event) {
        String key = createPositionKey(event.tradingPair(), event.positionSide());
        if (event.amount().compareTo(BigDecimal.ZERO) == 0) {
            // 포지션 종료
            positions.remove(key);
            return;
        }
        Position position = positions.get(key);
        if(position == null) {
            positions.put(key, Position.builder()
                    .tradingPair(event.tradingPair())
                    .positionSide(event.positionSide())
                    .amount(event.amount())
                    .unrealizedPnl(event.unrealizedPnl())
                    .entryPrice(event.entryPrice())
                    .build());
            return;
        }
        position.update(event.unrealizedPnl(), event.entryPrice(), event.amount());
    }

    @Override
    public void onStart() {

    }

    @Override
    public void onShutdown() {

    }
}
