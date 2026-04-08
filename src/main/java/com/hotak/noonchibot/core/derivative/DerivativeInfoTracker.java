package com.hotak.noonchibot.core.derivative;

import com.hotak.noonchibot.core.event.EventListener;
import com.hotak.noonchibot.core.event.ExchangeEventSubscriber;
import com.hotak.noonchibot.core.event.PositionUpdateEvent;
import com.hotak.noonchibot.core.orderbook.FundingInfoMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.context.SmartLifecycle;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * funding-Info, leverage, position에 대한 정보를 관리
 */
@RequiredArgsConstructor
public class DerivativeInfoTracker implements SmartLifecycle {
    /**
     * 펀딩비를 받기 위해 정해진 시간 대비 얼마나 일찍 포지션을 들고 있어야하는지, 5초면 1시에 펀딩비가 나온다면 1시 5초전을 의미
     */
    private static final Duration FUNDING_PAYMENT_SPAN_BEFORE = Duration.ofSeconds(5);
    private static final Duration FUNDING_PAYMENT_SPAN_AFTER = Duration.ofSeconds(5);

    private final ExchangeEventSubscriber eventSubscriber;
    private final PositionMode positionMode;
    private final Map<String, Integer> leverages = new HashMap<>();
    private final Map<String, Position> positions = new HashMap<>();
    private final Map<String, FundingInfoMessage> fundingInfo = new HashMap<>();

    private volatile boolean running = false;

    private final EventListener<PositionUpdateEvent> positionHandler = this::updatePosition;

    public Position getPosition(String tradingPair, PositionSide positionSide) {
        String key = createPositionKey(tradingPair, positionSide);
        return positions.get(key);
    }

    private String createPositionKey(String tradingPair, PositionSide positionSide) {
        if(positionMode == PositionMode.ONEWAY) {
            return tradingPair;
        }
        return tradingPair + "_" + positionSide.name();
    }

    public void updatePosition(PositionUpdateEvent event) {
        String key = createPositionKey(event.tradingPair(), event.positionSide());
        if (event.amount().compareTo(BigDecimal.ZERO) == 0) {
            // 포지션 종료
            positions.remove(key);
            return;
        }
        positions.computeIfPresent(key, (positionKey, position) -> position.toBuilder()
                .amount(event.amount())
                .entryPrice(event.entryPrice())
                .unrealizedPnl(event.unrealizedPnl())
                .build()
        );
    }

    @Override
    public void start() {
        eventSubscriber.subscribe(PositionUpdateEvent.class, positionHandler);
        running = true;

    }

    @Override
    public void stop() {
        eventSubscriber.unsubscribe(PositionUpdateEvent.class, positionHandler);
        running = false;
    }

    @Override
    public boolean isRunning() {
        return running;
    }
}
