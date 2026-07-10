package com.hotak.noonchibot.connector.web;

import com.hotak.noonchibot.core.TestTaskScheduler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TimeSynchronizerTest {

    @Test
    @DisplayName("serverTime 첫 호출 전에 서버 시간 offset을 초기화한다")
    void serverTimeInitializesServerOffsetBeforeReturning() {
        CountingServerTimeProvider provider = new CountingServerTimeProvider(System.currentTimeMillis());
        TimeSynchronizer synchronizer = new TimeSynchronizer(provider, new TestTaskScheduler());

        synchronizer.serverTime();

        assertThat(provider.callCount()).isOne();
    }

    @Test
    @DisplayName("onStart 시 서버 시간 offset을 즉시 초기화하고 주기 업데이트를 등록한다")
    void onStartInitializesServerOffsetAndSchedulesRefresh() {
        CountingServerTimeProvider provider = new CountingServerTimeProvider(System.currentTimeMillis());
        TestTaskScheduler scheduler = new TestTaskScheduler();
        TimeSynchronizer synchronizer = new TimeSynchronizer(provider, scheduler);

        synchronizer.onStart();

        assertThat(provider.callCount()).isOne();
        assertThat(scheduler.activeTaskCount()).isOne();
    }

    private static final class CountingServerTimeProvider implements ServerTimeProvider {
        private final long serverTimeMs;
        private int callCount;

        private CountingServerTimeProvider(long serverTimeMs) {
            this.serverTimeMs = serverTimeMs;
        }

        @Override
        public long getServerTimeMs() {
            callCount++;
            return serverTimeMs;
        }

        private int callCount() {
            return callCount;
        }
    }
}
