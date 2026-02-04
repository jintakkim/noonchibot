package com.hotak.noonchibot.core;

import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.mockito.Mockito;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class RealtimeClockTest {
    private static final Duration TICK_SIZE = Duration.ofMillis(100);

    @Test
    @DisplayName("생성자로 TimeIterator 전달시 저장되어야 한다")
    void constructor_withIterators_storesIterators() {
        var it1 = new TimeIterator();
        var it2 = new TimeIterator();
        var clock = new RealtimeClock(List.of(it1, it2), TICK_SIZE);

        assertThat(clock)
                .extracting("iterators")
                .asInstanceOf(InstanceOfAssertFactories.LIST)
                .hasSize(2)
                .containsExactly(it1, it2);
    }

    @Test
    @DisplayName("생성자로 빈 리스트 전달시 빈 상태로 초기화된다")
    void constructor_withEmptyList_initializesEmpty() {
        var clock = new RealtimeClock(List.of(), TICK_SIZE);

        assertThat(clock)
                .extracting("iterators")
                .asInstanceOf(InstanceOfAssertFactories.LIST)
                .isEmpty();
    }


    @Test
    @DisplayName("실행 중이 아닐 때 iterator 추가시 onStart를 호출하지 않는다")
    void addIterator_whenNotStarted_doesNotCallOnStart() {
        var iterator = Mockito.spy(new TimeIterator());
        var clock = new RealtimeClock(List.of(), TICK_SIZE);

        clock.addIterator(iterator);

        assertThat(clock)
                .extracting("iterators")
                .asInstanceOf(InstanceOfAssertFactories.LIST)
                .hasSize(1)
                .containsExactly(iterator);
        verify(iterator, never()).onStart(any(), any());
    }

    @Test
    @DisplayName("실행 중일 때 iterator 추가시 onStart를 호출한다")
    @Timeout(1)
    void addIterator_whenRunning_callsOnStart() throws InterruptedException {
        var iterator = Mockito.spy(new TimeIterator());
        var clock = new RealtimeClock(List.of(), TICK_SIZE);

        Thread thread = Thread.startVirtualThread(() -> clock.runUntil(Instant.now().plusMillis(200)));
        Thread.sleep(50);  // clock 시작 대기
        clock.addIterator(iterator);
        thread.join();

        verify(iterator, times(1)).onStart(any(), any());
    }

    @Test
    @DisplayName("실행 중일 때 iterator 제거시 onStop을 호출한다")
    @Timeout(1)
    void removeIterator_whenRunning_callsOnStop() throws InterruptedException {
        var iterator = Mockito.spy(new TimeIterator());
        var clock = new RealtimeClock(List.of(iterator), TICK_SIZE);

        Thread thread = Thread.startVirtualThread(() -> clock.runUntil(Instant.now().plusMillis(200)));
        Thread.sleep(50);  // clock 시작 대기
        clock.removeIterator(iterator);
        thread.interrupt();
        thread.join();

        verify(iterator, times(1)).onStop();
    }

    @Test
    @DisplayName("실행 중이 아닐 때 iterator 제거시 onStop을 호출하지 않는다")
    void removeIterator_whenNotStarted_doesNotCallOnStop() {
        var iterator = Mockito.spy(new TimeIterator());
        var clock = new RealtimeClock(List.of(iterator), TICK_SIZE);

        clock.removeIterator(iterator);

        verify(iterator, never()).onStop();
    }

    @Test
    @DisplayName("runUntil 호출시 등록된 모든 iterator의 onStart가 호출된다")
    @Timeout(1)
    void runUntil_callsOnStartForAllIterators() {
        var iterator1 = Mockito.spy(new TimeIterator());
        var iterator2 = Mockito.spy(new TimeIterator());
        var clock = new RealtimeClock(List.of(iterator1, iterator2), TICK_SIZE);

        clock.runUntil(Instant.now().plusMillis(10));

        verify(iterator1, times(1)).onStart(any(), any());
        verify(iterator2, times(1)).onStart(any(), any());
    }

    @Test
    @DisplayName("runUntil 호출시 틱마다 onTick이 호출된다")
    @Timeout(1)
    void runUntil_callsOnTickPerTick() {
        var iterator = Mockito.spy(new TimeIterator());
        var clock = new RealtimeClock(List.of(iterator), TICK_SIZE);

        clock.runUntil(Instant.now().plusMillis(250));

        verify(iterator, Mockito.atLeast(2)).onTick(any());
    }

    @Test
    @DisplayName("runUntil 종료시 onStop이 호출된다")
    @Timeout(1)
    void runUntil_whenEnds_callsOnStop() {
        var iterator = Mockito.spy(new TimeIterator());
        var clock = new RealtimeClock(List.of(iterator), TICK_SIZE);

        clock.runUntil(Instant.now().plusMillis(10));

        verify(iterator, times(1)).onStop();
    }

    @Test
    @DisplayName("interrupt 발생시 onStop 호출 후 종료한다")
    @Timeout(1)
    void runUntil_whenInterrupted_callsOnStopAndTerminates() throws InterruptedException {
        var iterator = Mockito.spy(new TimeIterator());
        var clock = new RealtimeClock(List.of(iterator), TICK_SIZE);

        Thread thread = Thread.startVirtualThread(() -> clock.runUntil(Instant.now().plusSeconds(100)));
        Thread.sleep(50);
        thread.interrupt();
        thread.join();

        verify(iterator, times(1)).onStop();
    }

    @Test
    @DisplayName("onTick에서 예외 발생해도 루프는 계속 실행된다")
    @Timeout(1)
    void runUntil_whenOnTickThrows_continuesLoop() {
        var iterator = Mockito.spy(new TimeIterator());
        var clock = new RealtimeClock(List.of(iterator), TICK_SIZE);
        doThrow(new RuntimeException("test exception")).when(iterator).onTick(any(Instant.class));

        clock.runUntil(Instant.now().plusMillis(250));

        verify(iterator, Mockito.atLeast(2)).onTick(any());
    }
}
