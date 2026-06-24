package com.hotak.noonchibot.core.event;

import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
public class Sequencer implements AutoCloseable {
    private final String key;
    private final BlockingQueue<Runnable> queue;
    private final Thread worker;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public Sequencer(String key, int capacity) {
        this.key = key;
        this.queue = new ArrayBlockingQueue<>(capacity);
        this.worker = Thread.ofPlatform()
                .name("seq-" + key)
                .daemon(false)
                .unstarted(this::loop);
    }

    void start() {
        if (running.compareAndSet(false, true)) worker.start();
    }

    /**
     * 만약 queue capacity가 차있으면 blocking 된다.
     */
    void submit(Runnable task) {
        try {
            queue.put(task);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("seq " + key + " submit interrupted", e);
        }
    }

    private void loop() {
        while (running.get() || !queue.isEmpty()) {
            try {
                Runnable task = queue.poll(100, TimeUnit.MILLISECONDS);
                if (task == null) continue;
                task.run();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception e) {
                log.error("error occurred while processing task", e);
            }
        }
    }

    @Override
    public void close() {
        if (running.compareAndSet(true, false)) {
            try {
                worker.join(Duration.ofSeconds(5));
                if(worker.isAlive()) {
                    worker.interrupt();
                    worker.join(Duration.ofSeconds(1));
                }
            } catch (InterruptedException e) {
                worker.interrupt();
                Thread.currentThread().interrupt();
            }
        }
    }
}
