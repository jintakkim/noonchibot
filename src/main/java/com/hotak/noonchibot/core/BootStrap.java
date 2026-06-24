package com.hotak.noonchibot.core;

import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Slf4j
public class BootStrap {
    private final List<LifecycleAware> components = new ArrayList<>();
    private List<LifecycleAware> sorted = List.of();
    private volatile boolean started = false;

    public BootStrap register(LifecycleAware component) {
        if (started) {
            throw new IllegalStateException("Cannot register after start");
        }
        components.add(component);
        return this;
    }

    public void start() {
        if (started) {
            log.warn("Bootstrap already started");
            return;
        }

        int startedCount = 0;
        sorted = components.stream()
                .sorted(Comparator.comparingInt(LifecycleAware::phase))
                .toList();

        try {
            log.info("BootStrap: Starting {} components", sorted.size());

            for (LifecycleAware component : sorted) {
                log.debug("Starting [phase={}]: {}", component.phase(), component.getClass().getSimpleName());
                component.onStart();
                startedCount++;
            }

            started = true;
        } catch (Exception e) {
            log.error("Bootstrap failed at component index {}, rolling back", startedCount, e);

            for (int i = startedCount - 1; i >= 0; i--) {
                try {
                    sorted.get(i).onShutdown();
                } catch (Exception rollback) {
                    log.error("Rollback shutdown failed: {}", sorted.get(i).getClass().getSimpleName(), rollback);
                }
            }

            sorted = List.of();
            started = false;
            throw e;
        }
    }

    public void shutdown() {
        if (!started) {
            log.warn("Bootstrap not started, nothing to shutdown");
            return;
        }

        for (LifecycleAware component : sorted.reversed()) {
            try {
                component.onShutdown();
            } catch (Exception e) {
                log.error("Error shutting down {}", component.getClass().getSimpleName(), e);
            }
        }

        sorted = List.of();
        started = false;
    }
}
