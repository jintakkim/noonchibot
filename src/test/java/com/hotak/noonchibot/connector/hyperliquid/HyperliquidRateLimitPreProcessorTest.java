package com.hotak.noonchibot.connector.hyperliquid;

import com.hotak.noonchibot.connector.web.RestRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import java.util.Collections;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class HyperliquidRateLimitPreProcessorTest {
    private final HyperliquidRateLimitPreProcessor processor =
            new HyperliquidRateLimitPreProcessor();

    @Test
    @DisplayName("가벼운 info 요청은 weight 2를 사용한다")
    void lightInfoRequestUsesWeightTwo() {
        RestRequest result = process(
                DerivativeApiSpec.INFO_PATH_URL,
                Map.of("type", "clearinghouseState")
        );

        assertThat(result.throttlerLimitId()).isEqualTo(DerivativeApiSpec.INFO_PATH_URL);
        assertThat(result.weightOverrides())
                .containsEntry(DerivativeApiSpec.REST_WEIGHT_POOL, 2);
    }

    @Test
    @DisplayName("일반 info 요청은 weight 20을 사용한다")
    void regularInfoRequestUsesWeightTwenty() {
        RestRequest result = process(
                DerivativeApiSpec.INFO_PATH_URL,
                Map.of("type", "meta")
        );

        assertThat(result.weightOverrides())
                .containsEntry(DerivativeApiSpec.REST_WEIGHT_POOL, 20);
    }

    @Test
    @DisplayName("exchange 요청은 batch 40개마다 weight가 증가한다")
    void exchangeRequestUsesBatchWeight() {
        RestRequest result = process(
                DerivativeApiSpec.EXCHANGE_PATH_URL,
                Map.of("action", Map.of(
                        "type", "order",
                        "orders", Collections.nCopies(40, Map.of())
                ))
        );

        assertThat(result.weightOverrides())
                .containsEntry(DerivativeApiSpec.REST_WEIGHT_POOL, 2);
    }

    private RestRequest process(String path, Map<String, Object> body) {
        return processor.process(RestRequest.builder()
                .method(HttpMethod.POST)
                .pathUrl(path)
                .body(body)
                .build());
    }
}
