package com.hotak.noonchibot.core.resilience;

import com.hotak.noonchibot.core.Exchange;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

class CircuitBreakerNamesTest {
    private static final List<Function<Exchange, String>> CIRCUIT_NAMES = List.of(
            CircuitBreakerNames::rest,
            CircuitBreakerNames::orderEntry,
            CircuitBreakerNames::orderCancel,
            CircuitBreakerNames::orderStatus,
            CircuitBreakerNames::orderBook,
            CircuitBreakerNames::balance,
            CircuitBreakerNames::trades,
            CircuitBreakerNames::tradingRules,
            CircuitBreakerNames::tradeFee,
            CircuitBreakerNames::funding,
            CircuitBreakerNames::userStream,
            CircuitBreakerNames::derivativeInfo,
            CircuitBreakerNames::serverTime,
            CircuitBreakerNames::transfer
    );

    @Test
    @DisplayName("거래소와 기능 조합별 서킷 이름은 서로 중복되지 않는다")
    void circuitNames_areUniqueAcrossExchangesAndOperations() {
        List<String> names = Arrays.stream(Exchange.values())
                .flatMap(exchange -> CIRCUIT_NAMES.stream().map(factory -> factory.apply(exchange)))
                .toList();

        assertThat(names)
                .doesNotHaveDuplicates()
                .hasSize(Exchange.values().length * CIRCUIT_NAMES.size());
    }

    @Test
    @DisplayName("서킷 이름은 거래소 ID를 prefix로 사용한다")
    void circuitNames_areScopedByExchangeId() {
        for (Exchange exchange : Exchange.values()) {
            assertThat(CIRCUIT_NAMES)
                    .allSatisfy(factory -> assertThat(factory.apply(exchange))
                            .startsWith(exchange.getId() + "."));
        }
    }
}
