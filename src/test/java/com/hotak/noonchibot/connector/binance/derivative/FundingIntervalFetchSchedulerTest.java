package com.hotak.noonchibot.connector.binance.derivative;

import com.hotak.noonchibot.core.TestTaskScheduler;
import com.hotak.noonchibot.core.event.TestEventPublisher;
import com.hotak.noonchibot.core.event.internal.derivative.FundingInfoEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class FundingIntervalFetchSchedulerTest {
    private TestEventPublisher eventPublisher;
    private TestTaskScheduler taskScheduler;
    private FundingIntervalFetchScheduler fundingIntervalFetchScheduler;

    @BeforeEach
    void setUp() {
        eventPublisher = new TestEventPublisher();
        taskScheduler = new TestTaskScheduler();
        fundingIntervalFetchScheduler = new FundingIntervalFetchScheduler(taskScheduler, eventPublisher);
    }

    @Nested
    @DisplayName("시작시")
    class OnStart {
        @Test
        @DisplayName("즉시 IntervalRestFetchRequested 이벤트를 발행한다")
        void publishesInitialRefreshImmediately() {
            fundingIntervalFetchScheduler.onStart();
            assertThat(eventPublisher.hasEventOfType(FundingInfoEvent.IntervalRestFetchRequested.class))
                    .isTrue();
        }

        @Test
        @DisplayName("TaskScheduler에 task를 등록한다")
        void registersTaskToScheduler() {
            fundingIntervalFetchScheduler.onStart();
            assertThat(taskScheduler.activeTaskCount()).isEqualTo(1);
        }


        @Test
        @DisplayName("등록된 task의 주기는 1시간이다")
        void registersTaskWithOneHourPeriod() {
            fundingIntervalFetchScheduler.onStart();

            assertThat(taskScheduler.onlyScheduledTask().period())
                    .isEqualTo(Duration.ofHours(1));
        }
    }
}
