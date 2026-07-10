package com.hotak.noonchibot.core.resilience;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.core.EventConsumer;
import io.github.resilience4j.core.Registry;
import io.github.resilience4j.core.registry.EntryAddedEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CircuitBreakerEventHandlerTest {
    @Test
    @DisplayName("생성 시 registry에 이미 등록된 서킷을 구독한다")
    void constructor_subscribesCircuitAlreadyPresentInRegistry() {
        CircuitBreakerRegistry circuitBreakerRegistry = CircuitBreakerRegistry.ofDefaults();
        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker("BINANCE_SPOT.order-entry");
        circuitBreaker.transitionToOpenState();
        ExchangeHealthRegistry healthRegistry = new ExchangeHealthRegistry();

        new CircuitBreakerEventHandler(circuitBreakerRegistry, healthRegistry);

        assertThat(healthRegistry.find(circuitBreaker.getName()))
                .hasValueSatisfying(health ->
                        assertThat(health.state()).isEqualTo(CircuitBreaker.State.OPEN));

        circuitBreaker.transitionToHalfOpenState();
        assertThat(healthRegistry.find(circuitBreaker.getName()))
                .hasValueSatisfying(health ->
                        assertThat(health.state()).isEqualTo(CircuitBreaker.State.HALF_OPEN));

        circuitBreaker.transitionToClosedState();
        assertThat(healthRegistry.find(circuitBreaker.getName()))
                .hasValueSatisfying(health ->
                        assertThat(health.state()).isEqualTo(CircuitBreaker.State.CLOSED));
    }

    @Test
    @DisplayName("핸들러 생성 후 registry에 추가된 서킷을 구독한다")
    void registryEntryEvent_subscribesCircuitAddedAfterHandlerCreation() {
        CircuitBreakerRegistry circuitBreakerRegistry = CircuitBreakerRegistry.ofDefaults();
        ExchangeHealthRegistry healthRegistry = new ExchangeHealthRegistry();
        new CircuitBreakerEventHandler(circuitBreakerRegistry, healthRegistry);

        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker("HYPERLIQUID.order-status");

        assertThat(healthRegistry.find(circuitBreaker.getName()))
                .hasValueSatisfying(health ->
                        assertThat(health.state()).isEqualTo(CircuitBreaker.State.CLOSED));

        circuitBreaker.transitionToOpenState();
        assertThat(healthRegistry.find(circuitBreaker.getName()))
                .hasValueSatisfying(health ->
                        assertThat(health.state()).isEqualTo(CircuitBreaker.State.OPEN));
    }

    @Test
    @DisplayName("상태 전이를 health registry에 반영하고 다른 서킷과 격리한다")
    void stateTransitions_updateAvailabilityAndPreserveCircuitIsolation() {
        CircuitBreakerRegistry circuitBreakerRegistry = CircuitBreakerRegistry.ofDefaults();
        ExchangeHealthRegistry healthRegistry = new ExchangeHealthRegistry();
        new CircuitBreakerEventHandler(circuitBreakerRegistry, healthRegistry);
        CircuitBreaker orderEntry = circuitBreakerRegistry.circuitBreaker("BINANCE_SPOT.order-entry");
        CircuitBreaker orderCancel = circuitBreakerRegistry.circuitBreaker("BINANCE_SPOT.order-cancel");

        orderEntry.transitionToOpenState();

        assertThat(healthRegistry.isAvailable(orderEntry.getName())).isFalse();
        assertThat(healthRegistry.isAvailable(orderCancel.getName())).isTrue();
        assertThat(healthRegistry.find(orderCancel.getName()))
                .hasValueSatisfying(health ->
                        assertThat(health.state()).isEqualTo(CircuitBreaker.State.CLOSED));

        orderEntry.transitionToHalfOpenState();

        assertThat(healthRegistry.isAvailable(orderEntry.getName())).isTrue();
        assertThat(healthRegistry.isAvailable(orderCancel.getName())).isTrue();
    }

    @Test
    @DisplayName("OPEN 상태의 거절 호출을 집계하고 실제 호출은 실행하지 않는다")
    void rejectedCalls_areCountedWithoutInvokingProtectedCall() {
        CircuitBreakerRegistry circuitBreakerRegistry = CircuitBreakerRegistry.ofDefaults();
        ExchangeHealthRegistry healthRegistry = new ExchangeHealthRegistry();
        new CircuitBreakerEventHandler(circuitBreakerRegistry, healthRegistry);
        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker("BINANCE_SPOT.order-entry");
        circuitBreaker.transitionToOpenState();
        int[] protectedCallCount = {0};
        Runnable protectedCall = CircuitBreaker.decorateRunnable(
                circuitBreaker,
                () -> protectedCallCount[0]++
        );

        assertThatThrownBy(protectedCall::run).isInstanceOf(CallNotPermittedException.class);
        assertThatThrownBy(protectedCall::run).isInstanceOf(CallNotPermittedException.class);

        assertThat(protectedCallCount[0]).isZero();
        assertThat(healthRegistry.find(circuitBreaker.getName()))
                .hasValueSatisfying(health -> {
                    assertThat(health.state()).isEqualTo(CircuitBreaker.State.OPEN);
                    assertThat(health.callNotPermittedCount()).isEqualTo(2);
                });
    }

    @Test
    @DisplayName("동일한 registry entry event가 반복되어도 중복 구독하지 않는다")
    @SuppressWarnings("unchecked")
    void duplicateRegistryEntryEvents_doNotSubscribeCircuitTwice() {
        CircuitBreaker circuitBreaker = CircuitBreaker.ofDefaults("BINANCE_SPOT.order-entry");
        CircuitBreakerRegistry circuitBreakerRegistry = mock(CircuitBreakerRegistry.class);
        Registry.EventPublisher<CircuitBreaker> registryEventPublisher = mock(Registry.EventPublisher.class);
        when(circuitBreakerRegistry.getAllCircuitBreakers()).thenReturn(Set.of(circuitBreaker));
        when(circuitBreakerRegistry.getEventPublisher()).thenReturn(registryEventPublisher);
        when(registryEventPublisher.onEntryAdded(any())).thenReturn(registryEventPublisher);
        ArgumentCaptor<EventConsumer<EntryAddedEvent<CircuitBreaker>>> entryAddedConsumerCaptor =
                ArgumentCaptor.forClass(EventConsumer.class);
        ExchangeHealthRegistry healthRegistry = new ExchangeHealthRegistry();

        new CircuitBreakerEventHandler(circuitBreakerRegistry, healthRegistry);
        verify(registryEventPublisher).onEntryAdded(entryAddedConsumerCaptor.capture());
        EntryAddedEvent<CircuitBreaker> duplicateEvent = mock(EntryAddedEvent.class);
        when(duplicateEvent.getAddedEntry()).thenReturn(circuitBreaker);
        entryAddedConsumerCaptor.getValue().consumeEvent(duplicateEvent);
        entryAddedConsumerCaptor.getValue().consumeEvent(duplicateEvent);

        circuitBreaker.transitionToOpenState();
        Runnable protectedCall = CircuitBreaker.decorateRunnable(circuitBreaker, () -> { });
        assertThatThrownBy(protectedCall::run).isInstanceOf(CallNotPermittedException.class);

        assertThat(healthRegistry.find(circuitBreaker.getName()))
                .hasValueSatisfying(health ->
                        assertThat(health.callNotPermittedCount()).isOne());
    }
}
