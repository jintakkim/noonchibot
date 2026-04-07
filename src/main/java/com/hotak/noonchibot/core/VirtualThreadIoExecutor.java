package com.hotak.noonchibot.core;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.task.VirtualThreadTaskExecutor;

@Slf4j
public class VirtualThreadIoExecutor extends VirtualThreadTaskExecutor implements IoExecutor {
    public VirtualThreadIoExecutor() {
        super("io-executor-");
    }

    @Override
    public void execute(Runnable task) {
        super.execute(() -> {
            try {
                task.run();
            } catch (Exception e) {
                log.error("IO task failed", e);
                throw e;
            }
        });
    }
}