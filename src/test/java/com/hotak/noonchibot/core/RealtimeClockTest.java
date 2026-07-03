package com.hotak.noonchibot.core;

import com.hotak.noonchibot.core.event.SequentialDispatcher;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Queue;

import static org.assertj.core.api.Assertions.assertThat;

class RealtimeClockTest {
    private static final Duration TICK_SIZE = Duration.ofMillis(100);

    @Test
    @DisplayName("start와 tick은 등록된 sequential dispatcher를 통해 실행한다")
    void lifecycleAndTickUseSequentialDispatcher() {
        TestTaskScheduler scheduler = new TestTaskScheduler();
        RecordingDispatcher dispatcher = new RecordingDispatcher();
        RecordingIterator iterator = new RecordingIterator();
        RealtimeClock clock = new RealtimeClock(scheduler, TICK_SIZE);
        clock.addIterator(iterator, dispatcher);

        clock.start();

        assertThat(iterator.startCount).isZero();
        dispatcher.runNext();
        assertThat(iterator.startCount).isEqualTo(1);

        scheduler.onlyScheduledTask().task().run();
        assertThat(iterator.tickCount).isZero();
        dispatcher.runNext();
        assertThat(iterator.tickCount).isEqualTo(1);
    }

    @Test
    @DisplayName("stop은 예약을 취소하고 iterator 종료를 sequential dispatcher에 전달한다")
    void stopCancelsScheduleAndDispatchesIteratorStop() {
        TestTaskScheduler scheduler = new TestTaskScheduler();
        RecordingDispatcher dispatcher = new RecordingDispatcher();
        RecordingIterator iterator = new RecordingIterator();
        RealtimeClock clock = new RealtimeClock(scheduler, TICK_SIZE);
        clock.addIterator(iterator, dispatcher);
        clock.start();
        dispatcher.runNext();

        clock.stop();

        assertThat(scheduler.onlyScheduledTask().isCancelled()).isTrue();
        assertThat(iterator.stopCount).isZero();
        dispatcher.runNext();
        assertThat(iterator.stopCount).isEqualTo(1);
        assertThat(clock.isRunning()).isFalse();
    }

    private static final class RecordingDispatcher implements SequentialDispatcher {
        private final Queue<Runnable> tasks = new ArrayDeque<>();

        @Override
        public void dispatchSequential(Runnable task) {
            tasks.add(task);
        }

        void runNext() {
            tasks.remove().run();
        }
    }

    private static final class RecordingIterator extends TimeIterator {
        private int startCount;
        private int tickCount;
        private int stopCount;

        @Override
        public void onStart(Clock clock, Instant timestamp) {
            super.onStart(clock, timestamp);
            startCount++;
        }

        @Override
        public void onTick(Instant timestamp) {
            super.onTick(timestamp);
            tickCount++;
        }

        @Override
        public void onStop() {
            super.onStop();
            stopCount++;
        }
    }
}
