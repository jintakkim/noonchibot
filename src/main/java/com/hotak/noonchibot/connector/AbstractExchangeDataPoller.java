package com.hotak.noonchibot.connector;

import com.hotak.noonchibot.core.TimeIterator;
import com.hotak.noonchibot.core.datatype.OrderStreamStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.core.task.AsyncTaskExecutor;

import java.time.Duration;
import java.time.Instant;

@RequiredArgsConstructor
public abstract class AbstractExchangeDataPoller extends TimeIterator {
    protected static final Duration SHORT_POLL_INTERVAL = Duration.ofSeconds(5);
    protected static final Duration LONG_POLL_INTERVAL = Duration.ofMinutes(2);
    protected static final Duration TICK_INTERVAL_LIMIT = Duration.ofMinutes(1);

    private final OrderStreamStatus orderStreamStatus;
    protected final AsyncTaskExecutor taskExecutor;
    private volatile Instant lastPollTimestamp;

    @Override
    public void onTick(Instant timestamp) {
        super.onTick(timestamp);
        pollStatusIfNeeded();
    }

    protected abstract void pollData();

    private void pollStatusIfNeeded() {
        Duration interval = getPollInterval(getCurrentTimestamp());
        Duration elapsed = Duration.between(lastPollTimestamp, getCurrentTimestamp());
        if (elapsed.compareTo(interval) >= 0) {
            taskExecutor.execute(() -> {
                pollData();
                lastPollTimestamp = getCurrentTimestamp();
            });
        }
    }

    /**
     * 폴링 주기를 결정한다.
     * WebSocket이 정상이면 긴 주기(백업용), 끊겼으면 짧은 주기로 폴링
     *
     * @param timestamp 현재 시간
     * @return 폴링 주기
     */
    private Duration getPollInterval(Instant timestamp) {
        Instant lastUserStreamMessageTime = Instant.MIN;
        Instant lastRecvTime = orderStreamStatus.getLastRecvTime();
        if(lastRecvTime != null) lastUserStreamMessageTime = lastRecvTime;
        Duration lastRecvDiff = Duration.between(lastUserStreamMessageTime, timestamp);
        return lastRecvDiff.compareTo(TICK_INTERVAL_LIMIT) > 0 ? SHORT_POLL_INTERVAL : LONG_POLL_INTERVAL;
    }

}
