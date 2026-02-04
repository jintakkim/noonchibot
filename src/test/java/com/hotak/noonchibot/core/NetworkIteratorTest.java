package com.hotak.noonchibot.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

public class NetworkIteratorTest {
    private static final Duration CHECK_NETWORK_INTERVAL = Duration.ofMillis(100);
    private static final Duration NETWORK_ERROR_WAIT_TIME = Duration.ofMillis(200);
    private static final Instant START_TIMESTAMP = Instant.parse("2021-01-01T00:00:00Z");

    static class TestNetworkIterator extends NetworkIterator {
        private volatile NetworkStatus checkNetworkResult = NetworkStatus.NOT_CONNECTED;
        public final CountDownLatch startNetworkCalledLatch = new CountDownLatch(1);
        public final CountDownLatch stopNetworkCalledLatch = new CountDownLatch(1);
        public final AtomicInteger startNetworkCallCount = new AtomicInteger(0);

        TestNetworkIterator() {
            super(CHECK_NETWORK_INTERVAL, NETWORK_ERROR_WAIT_TIME);
        }

        @Override
        protected NetworkStatus checkNetwork() {
            return checkNetworkResult;
        }

        @Override
        protected void startNetwork() {
            startNetworkCallCount.getAndIncrement();
            startNetworkCalledLatch.countDown();
        }

        @Override
        protected void stopNetwork() {
            stopNetworkCalledLatch.countDown();
        }

        void setCheckNetworkResult(NetworkStatus status) {
            this.checkNetworkResult = status;
        }

        Thread getCheckNetworkTask() {
            return (Thread) ReflectionTestUtils.getField(this, "checkNetworkTask");
        }
    }

    private Clock createClock(TimeIterator iterator) {
        return new RealtimeClock(List.of(iterator), Duration.ofMillis(100));
    }

    @Test
    @DisplayName("onStart 호출 시 상태가 NOT_CONNECTED로 변경된다")
    void onStart_setsStatusToNotConnected() {
        var iterator = new TestNetworkIterator();
        var clock = createClock(iterator);
        iterator.onStart(clock, START_TIMESTAMP);
        assertThat(iterator.getStatus()).isEqualTo(NetworkIterator.NetworkStatus.NOT_CONNECTED);
    }

    @Test
    @DisplayName("onStart 호출 시 checkNetworkTask가 시작된다")
    void onStart_startsCheckNetworkTask() {
        var iterator = new TestNetworkIterator();
        var clock = createClock(iterator);
        iterator.onStart(clock, START_TIMESTAMP);
        assertThat(iterator.getCheckNetworkTask()).isNotNull();
        assertThat(iterator.getCheckNetworkTask().isAlive()).isTrue();
    }

    @Test
    @DisplayName("onStop 호출 시 상태가 STOPPED로 변경된다")
    void onStop_setsStatusToStopped() {
        var iterator = new TestNetworkIterator();
        var clock = createClock(iterator);
        iterator.onStart(clock, START_TIMESTAMP);
        iterator.onStop();
        assertThat(iterator.getStatus()).isEqualTo(NetworkIterator.NetworkStatus.STOPPED);
    }

    @Test
    @DisplayName("onStop 호출 시 checkNetworkTask가 종료된다")
    void onStop_stopsCheckNetworkTask() throws InterruptedException {
        var iterator = new TestNetworkIterator();
        var clock = createClock(iterator);
        iterator.onStart(clock, START_TIMESTAMP);
        Thread task = iterator.getCheckNetworkTask();
        iterator.onStop();
        task.join(1000);
        assertThat(task.isAlive()).isFalse();
    }

    @Test
    @DisplayName("onStop 호출 시 stopNetwork가 호출된다")
    void onStop_callsStopNetwork() {
        var iterator = new TestNetworkIterator();
        var clock = createClock(iterator);
        iterator.onStart(clock, START_TIMESTAMP);
        iterator.onStop();
        assertThat(iterator.startNetworkCallCount.get()).isEqualTo(0);
    }

    @Test
    @DisplayName("checkNetwork가 CONNECTED를 반환하면 startNetwork가 호출된다")
    void whenConnected_callsStartNetwork() throws InterruptedException {
        var iterator = new TestNetworkIterator();
        var clock = createClock(iterator);
        iterator.setCheckNetworkResult(NetworkIterator.NetworkStatus.CONNECTED);
        iterator.onStart(clock, START_TIMESTAMP);
        boolean called = iterator.startNetworkCalledLatch.await(1, TimeUnit.SECONDS);
        assertThat(called).isTrue();;
        assertThat(iterator.getStatus()).isEqualTo(NetworkIterator.NetworkStatus.CONNECTED);
    }

    @Test
    @DisplayName("checkNetwork가 NOT_CONNECTED를 반환하면 stopNetwork가 호출된다")
    void whenDisconnected_callsStopNetwork() throws InterruptedException {
        var iterator = new TestNetworkIterator();
        var clock = createClock(iterator);
        iterator.setCheckNetworkResult(NetworkIterator.NetworkStatus.CONNECTED);
        iterator.onStart(clock, START_TIMESTAMP);
        //연결 대기
        boolean startNetworkCalled = iterator.startNetworkCalledLatch.await(1, TimeUnit.SECONDS);
        assertThat(startNetworkCalled).isTrue();
        // CONNECTED -> NOT_CONNECTED 전환
        iterator.setCheckNetworkResult(NetworkIterator.NetworkStatus.NOT_CONNECTED);
        boolean stopNetworkCalled = iterator.stopNetworkCalledLatch.await(1, TimeUnit.SECONDS);
        assertThat(stopNetworkCalled).isTrue();
        assertThat(iterator.getStatus()).isEqualTo(NetworkIterator.NetworkStatus.NOT_CONNECTED);
    }

    @Test
    @DisplayName("상태가 변경되지 않으면 startNetwork/stopNetwork가 호출되지 않는다")
    void whenStatusUnchanged_doesNotCallNetworkMethods() throws InterruptedException {
        var iterator = new TestNetworkIterator();
        var clock = createClock(iterator);
        iterator.setCheckNetworkResult(NetworkIterator.NetworkStatus.CONNECTED);
        iterator.onStart(clock, START_TIMESTAMP);
        Thread.sleep(350); //루프 틱 간격은 100ms
        iterator.onStop();
        assertThat(iterator.startNetworkCallCount.get()).isEqualTo(1); // 최초 1번만 호출
        assertThat(iterator.stopNetworkCalledLatch.getCount()).isEqualTo(0);
    }

    @Test
    @DisplayName("checkNetwork에서 예외 발생 시 상태가 NOT_CONNECTED로 변경된다")
    void whenException_setsStatusToNotConnected() throws InterruptedException {
        var iterator = Mockito.spy(new TestNetworkIterator());
        var clock = createClock(iterator);
        Mockito.when(iterator.checkNetwork()).thenThrow(new RuntimeException());
        iterator.onStart(clock, START_TIMESTAMP);
        Thread.sleep(100); // 예외 처리 대기
        assertThat(iterator.getStatus()).isEqualTo(NetworkIterator.NetworkStatus.NOT_CONNECTED);
    }

    @Test
    @DisplayName("CONNECTED 상태가 되면 lastConnectedTimestamp가 설정된다")
    void whenConnected_setsLastConnectedTimestamp() throws InterruptedException {
        var iterator = new TestNetworkIterator();
        var clock = createClock(iterator);
        iterator.setCheckNetworkResult(NetworkIterator.NetworkStatus.CONNECTED);

        iterator.onStart(clock, START_TIMESTAMP);
        boolean called = iterator.startNetworkCalledLatch.await(1, TimeUnit.SECONDS);
        assertThat(called).isTrue();;
        assertThat(iterator.getLastConnectedTimestamp()).isNotNull();
    }

    @Test
    @DisplayName("생성 시 상태는 STOPPED이다")
    void initialStatus_isStopped() {
        var iterator = new TestNetworkIterator();
        assertThat(iterator.getStatus()).isEqualTo(NetworkIterator.NetworkStatus.STOPPED);
    }

    @Test
    @DisplayName("생성 시 lastConnectedTimestamp는 null이다")
    void initialLastConnectedTimestamp_isNull() {
        var iterator = new TestNetworkIterator();
        assertThat(iterator.getLastConnectedTimestamp()).isNull();
    }
}
