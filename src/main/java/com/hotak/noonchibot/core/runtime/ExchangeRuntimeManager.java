package com.hotak.noonchibot.core.runtime;

import com.hotak.noonchibot.core.Exchange;
import com.hotak.noonchibot.core.UnsupportedExchangeException;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public final class ExchangeRuntimeManager implements SmartInitializingSingleton {
    private final List<ExchangeContextFactory> factories;
    private final Map<Exchange, RuntimeSlot> slots = new ConcurrentHashMap<>();

    @Override
    public void afterSingletonsInstantiated() {
        factories.forEach(this::initialize);
    }

    public void start(Exchange exchange) {
        RuntimeSlot slot = requiredSlot(exchange);
        synchronized (slot.lifecycleMonitor) {
            if (slot.status == ExchangeRuntimeStatus.RUNNING) {
                return;
            }
            slot.status = ExchangeRuntimeStatus.STARTING;
            slot.lastFailure = null;
            try {
                slot.context.start();
                slot.status = ExchangeRuntimeStatus.RUNNING;
            } catch (RuntimeException exception) {
                slot.lastFailure = exception;
                slot.status = ExchangeRuntimeStatus.FAILED;
                throw exception;
            }
        }
    }

    public void stop(Exchange exchange) {
        RuntimeSlot slot = slots.get(Objects.requireNonNull(exchange, "exchange"));
        if (slot == null) {
            return;
        }
        synchronized (slot.lifecycleMonitor) {
            if (slot.status == ExchangeRuntimeStatus.STOPPED) {
                return;
            }

            slot.status = ExchangeRuntimeStatus.STOPPING;
            try {
                slot.context.stop();
                slot.status = ExchangeRuntimeStatus.STOPPED;
                slot.lastFailure = null;
            } catch (RuntimeException exception) {
                slot.status = ExchangeRuntimeStatus.FAILED;
                slot.lastFailure = exception;
                throw exception;
            }
        }
    }

    public Set<Exchange> getActiveExchanges() {
        return slots.entrySet().stream()
                .filter(entry -> entry.getValue().status == ExchangeRuntimeStatus.RUNNING)
                .map(Map.Entry::getKey)
                .collect(Collectors.toUnmodifiableSet());
    }

    public ExchangeRuntimeState state(Exchange exchange) {
        RuntimeSlot slot = slots.get(Objects.requireNonNull(exchange, "exchange"));
        if (slot == null) {
            return new ExchangeRuntimeState(exchange, ExchangeRuntimeStatus.STOPPED, Optional.empty());
        }
        synchronized (slot.lifecycleMonitor) {
            return new ExchangeRuntimeState(exchange, slot.status, Optional.ofNullable(slot.lastFailure));
        }
    }

    public Map<Exchange, ExchangeRuntimeState> states() {
        Map<Exchange, ExchangeRuntimeState> result = new EnumMap<>(Exchange.class);
        slots.keySet().forEach(exchange -> result.put(exchange, state(exchange)));
        return Map.copyOf(result);
    }

    boolean isConfigured(Exchange exchange) {
        return slots.containsKey(Objects.requireNonNull(exchange, "exchange"));
    }

    <T> T getBean(Exchange exchange, Class<T> beanType) {
        RuntimeSlot slot = requiredSlot(exchange);
        synchronized (slot.lifecycleMonitor) {
            return slot.context.getBean(beanType);
        }
    }

    @PreDestroy
    void shutdown() {
        for (RuntimeSlot slot : slots.values()) {
            synchronized (slot.lifecycleMonitor) {
                try {
                    slot.context.close();
                } finally {
                    slot.status = ExchangeRuntimeStatus.STOPPED;
                }
            }
        }
    }

    private void initialize(ExchangeContextFactory factory) {
        Exchange exchange = Objects.requireNonNull(factory.exchange(), "factory exchange");
        if (slots.containsKey(exchange)) {
            throw new IllegalStateException("Duplicate exchange context factory: " + exchange);
        }

        ConfigurableApplicationContext context = factory.buildContext();
        RuntimeSlot slot = new RuntimeSlot(context);
        try {
            context.refresh();
            slot.status = ExchangeRuntimeStatus.RUNNING;
        } catch (RuntimeException exception) {
            closeQuietly(context);
            throw exception;
        }

        RuntimeSlot previous = slots.putIfAbsent(exchange, slot);
        if (previous != null) {
            context.close();
            throw new IllegalStateException("Duplicate exchange context factory: " + exchange);
        }
    }

    private RuntimeSlot requiredSlot(Exchange exchange) {
        RuntimeSlot slot = slots.get(Objects.requireNonNull(exchange, "exchange"));
        if (slot == null) {
            throw new UnsupportedExchangeException("Exchange is not configured: " + exchange);
        }
        return slot;
    }

    private void closeQuietly(ConfigurableApplicationContext context) {
        if (context == null) {
            return;
        }
        try {
            context.close();
        } catch (RuntimeException ignored) {
            // 원래 초기화 실패를 보존한다.
        }
    }

    private static final class RuntimeSlot {
        private final Object lifecycleMonitor = new Object();
        private final ConfigurableApplicationContext context;
        private volatile ExchangeRuntimeStatus status = ExchangeRuntimeStatus.STOPPED;
        private Throwable lastFailure;

        private RuntimeSlot(ConfigurableApplicationContext context) {
            this.context = context;
        }
    }
}
