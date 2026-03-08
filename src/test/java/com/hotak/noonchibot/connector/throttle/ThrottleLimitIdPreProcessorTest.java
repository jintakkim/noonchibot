package com.hotak.noonchibot.connector.throttle;

import com.hotak.noonchibot.connector.web.RestRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import static org.assertj.core.api.Assertions.assertThat;

class ThrottlerLimitIdPreProcessorTest {
    private final ThrottlerLimitIdPreProcessor processor = new ThrottlerLimitIdPreProcessor();

    @DisplayName("limitId가 있으면 그대로 유지한다")
    @Test
    void keepExistingLimitId() {
        RestRequest request = RestRequest.builder()
                .method(HttpMethod.GET)
                .pathUrl("/api/order")
                .throttlerLimitId("custom-limit")
                .build();

        RestRequest result = processor.process(request);

        assertThat(result.throttlerLimitId()).isEqualTo("custom-limit");
    }

    @DisplayName("limitId가 null이면 pathUrl을 limitId로 사용한다")
    @Test
    void usePathUrlWhenLimitIdIsNull() {
        RestRequest request = RestRequest.builder()
                .method(HttpMethod.GET)
                .pathUrl("/api/order")
                .throttlerLimitId(null)
                .build();

        RestRequest result = processor.process(request);

        assertThat(result.throttlerLimitId()).isEqualTo("/api/order");
    }
}