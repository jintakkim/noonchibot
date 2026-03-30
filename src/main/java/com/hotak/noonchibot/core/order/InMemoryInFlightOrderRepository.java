package com.hotak.noonchibot.core.order;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public class InMemoryInFlightOrderRepository implements InFlightOrderRepository {
    private final Map<String, InFlightOrder> orders = new ConcurrentHashMap<>();

    @Override
    public void save(InFlightOrder inFlightOrder) {
        orders.put(inFlightOrder.getClientOrderId(), inFlightOrder);

    }

    @Override
    public void deleteByClientId(String clientOrderId) {
        orders.remove(clientOrderId);
    }

    @Override
    public void update(String clientOrderId, Consumer<InFlightOrder> action) {
        orders.computeIfPresent(clientOrderId, (key, order) -> {
            action.accept(order);
            return order;
        });
    }

    @Override
    public Optional<InFlightOrder> findById(String clientOrderId, String exchangeOrderId) {
        return Optional.empty();
    }

    @Override
    public Optional<InFlightOrder> findByClientId(String clientOrderId) {
        return Optional.empty();
    }

    @Override
    public Optional<InFlightOrder> findByExchangeId(String clientOrderId) {
        return Optional.empty();
    }
}
