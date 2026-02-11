package com.hotak.noonchibot.core;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.time.Instant;

@RequiredArgsConstructor
@Slf4j
public abstract class NetworkIterator extends TimeIterator {
    private static final Duration DEFAULT_CHECK_NETWORK_INTERVAL = Duration.ofSeconds(10);
    private static final Duration DEFAULT_NETWORK_ERROR_WAIT_TIME = Duration.ofMinutes(1);

    public enum NetworkStatus {STOPPED, NOT_CONNECTED, CONNECTED}

    private final Duration checkNetworkInterval;
    private final Duration networkErrorWaitTime;
    @Getter
    private volatile NetworkStatus status = NetworkStatus.STOPPED;
    @Getter
    private volatile Instant lastConnectedTimestamp;
    //onStart, onStop은 같은 스레드에서 처리되기 때문에 volatile이 필요 없다.
    private Thread checkNetworkTask;

    /**
     * network blocking method
     * warning: 구현시 네트워크 타임아웃을 설정해야한다.
     * @return 현제 네트워크가 연결 가능한지 확인(연결가능하다면 CONNECTED로 리턴, 연결 불가능하다면 NOT_CONNECTED로 리턴)
     */
    protected abstract NetworkStatus checkNetwork();
    /**
     * 네트워크 상태가 Connected일때 서버에 연결한다.
     * warning: 구현시 네트워크 타임아웃을 설정해야한다.
     */
    protected abstract void startNetwork();
    /**
     * 연결 해제 한다.
     * warning: 구현시 네트워크 타임아웃을 설정해야한다.
     */
    protected abstract void stopNetwork();

    public NetworkIterator() {
        this.checkNetworkInterval = DEFAULT_CHECK_NETWORK_INTERVAL;
        this.networkErrorWaitTime = DEFAULT_NETWORK_ERROR_WAIT_TIME;
    }

    private void checkNetworkLoop() {
        while (!Thread.currentThread().isInterrupted()) {
            NetworkStatus lastStatus = status;
            try {
                NetworkStatus newStatus = checkNetwork();
                if (newStatus != lastStatus) {
                    status = newStatus;
                    if (status == NetworkStatus.CONNECTED) {
                        log.info("네트워크 상태가 {}로 변경되었습니다, 서버 연결을 시도합니다.", status);
                        startNetwork();
                        lastConnectedTimestamp = getCurrentTimestamp();
                    } else {
                        log.info("네트워크 상태가 {}로 변경되었습니다, 연결 해제를 시도합니다", status);
                        stopNetwork();
                    }
                }
                Thread.sleep(checkNetworkInterval);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                status = NetworkStatus.NOT_CONNECTED;
                log.error("네트워크 상태 체크중 예외 발생, {}초 후 재연결을 시도합니다", networkErrorWaitTime.toSeconds(), e);
                try {
                    Thread.sleep(networkErrorWaitTime);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    @Override
    public void onStart(Clock clock, Instant timestamp) {
        super.onStart(clock, timestamp);
        checkNetworkTask = Thread.startVirtualThread(this::checkNetworkLoop);
        status = NetworkStatus.NOT_CONNECTED;
    }

    @Override
    public void onStop() {
        super.onStop();
        if(checkNetworkTask != null) {
            checkNetworkTask.interrupt();
            checkNetworkTask = null;
        }
        status = NetworkStatus.STOPPED;
        stopNetwork();
    }
}
