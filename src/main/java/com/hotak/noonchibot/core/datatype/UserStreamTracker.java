package com.hotak.noonchibot.core.datatype;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

@Slf4j
@RequiredArgsConstructor
public class UserStreamTracker {
    private final UserStreamTrackerDatasource datasource;

    public final BlockingQueue<Object> userStream = new LinkedBlockingQueue<>();

    private volatile boolean running = false;

    public synchronized void start() {
        if (running) return;
        running = true;
        datasource.listenForUserStream(userStream);
    }

    public synchronized void stop() {
        running = false;
        datasource.stop();
        userStream.clear();
    }

    public boolean isRunning() {
        return running && datasource.isConnected();
    }

    public Instant getLastRecvTime() {
        return datasource.getLastRecvTime();
    }

}
