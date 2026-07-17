package com.hotak.noonchibot.connector.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("RestRequest")
class RestRequestTest {

    @Nested
    @DisplayName("필수값 검증")
    class RequiredFields {

        @Test
        @DisplayName("HTTP method가 없으면 생성할 수 없다")
        void methodIsRequired() {
            assertThatThrownBy(() -> RestRequest.builder()
                    .pathUrl("/orders")
                    .build())
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("method is required");
        }

        @ParameterizedTest
        @NullSource
        @ValueSource(strings = {"", "   "})
        @DisplayName("pathUrl이 null 또는 공백이면 생성할 수 없다")
        void pathUrlIsRequired(String pathUrl) {
            assertThatThrownBy(() -> RestRequest.builder()
                    .method(HttpMethod.GET)
                    .pathUrl(pathUrl)
                    .build())
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("pathUrl is required");
        }

        @Test
        @DisplayName("weightOverrides가 null이면 생성할 수 없다")
        void weightOverridesMustNotBeNull() {
            assertThatThrownBy(() -> RestRequest.builder()
                    .method(HttpMethod.GET)
                    .pathUrl("/orders")
                    .weightOverrides(null)
                    .build())
                    .isInstanceOf(NullPointerException.class)
                    .hasMessage("weightOverrides must not be null");
        }
    }

    @Nested
    @DisplayName("Builder 기본값")
    class BuilderDefaults {

        @Test
        @DisplayName("선택값을 생략하면 REST 실행 기본 정책을 사용한다")
        void usesExecutionDefaults() {
            RestRequest request = RestRequest.builder()
                    .method(HttpMethod.GET)
                    .pathUrl("/time")
                    .build();

            assertThat(request.params()).isNull();
            assertThat(request.body()).isNull();
            assertThat(request.headers()).isNotNull();
            assertThat(request.headers().isEmpty()).isTrue();
            assertThat(request.authRequired()).isFalse();
            assertThat(request.throttlerLimitId()).isNull();
            assertThat(request.weightOverrides()).isEmpty();
            assertThat(request.throwError()).isTrue();
        }
    }

    @Nested
    @DisplayName("필드 구성")
    class FieldConfiguration {

        @Test
        @DisplayName("Builder에 전달한 모든 값을 요청에 보존한다")
        void preservesConfiguredValues() {
            Map<String, Object> params = Map.of("symbol", "BTCUSDT");
            Map<String, Object> body = Map.of("side", "BUY");
            HttpHeaders headers = new HttpHeaders();
            headers.add("X-API-KEY", "key");
            Map<String, Integer> weightOverrides = Map.of("REQUEST_WEIGHT", 5);

            RestRequest request = RestRequest.builder()
                    .method(HttpMethod.POST)
                    .pathUrl("/orders")
                    .params(params)
                    .body(body)
                    .headers(headers)
                    .authRequired(true)
                    .throttlerLimitId("ORDER")
                    .weightOverrides(weightOverrides)
                    .throwError(false)
                    .build();

            assertThat(request.method()).isEqualTo(HttpMethod.POST);
            assertThat(request.pathUrl()).isEqualTo("/orders");
            assertThat(request.params()).isEqualTo(params);
            assertThat(request.body()).isEqualTo(body);
            assertThat(request.headers()).isEqualTo(headers);
            assertThat(request.authRequired()).isTrue();
            assertThat(request.throttlerLimitId()).isEqualTo("ORDER");
            assertThat(request.weightOverrides()).isEqualTo(weightOverrides);
            assertThat(request.throwError()).isFalse();
        }
    }

    @Nested
    @DisplayName("toBuilder")
    class ToBuilder {

        @Test
        @DisplayName("모든 필드를 보존한다")
        void preservesAllFields() {
            HttpHeaders headers = new HttpHeaders();
            headers.add("X-API-KEY", "key");
            RestRequest original = RestRequest.builder()
                    .method(HttpMethod.POST)
                    .pathUrl("/orders")
                    .params(Map.of("symbol", "BTCUSDT"))
                    .body(Map.of("side", "BUY"))
                    .headers(headers)
                    .authRequired(true)
                    .throttlerLimitId("ORDER")
                    .weightOverrides(Map.of("REQUEST_WEIGHT", 5))
                    .throwError(false)
                    .build();

            RestRequest copied = original.toBuilder().build();

            assertThat(copied).isEqualTo(original);
        }

        @Test
        @DisplayName("선택한 필드만 변경하고 원본은 유지한다")
        void changesOnlySelectedFields() {
            RestRequest original = RestRequest.builder()
                    .method(HttpMethod.GET)
                    .pathUrl("/orders")
                    .params(Map.of("symbol", "BTCUSDT"))
                    .authRequired(true)
                    .build();

            RestRequest changed = original.toBuilder()
                    .pathUrl("/open-orders")
                    .throwError(false)
                    .build();

            assertThat(changed.method()).isEqualTo(original.method());
            assertThat(changed.params()).isEqualTo(original.params());
            assertThat(changed.authRequired()).isEqualTo(original.authRequired());
            assertThat(changed.pathUrl()).isEqualTo("/open-orders");
            assertThat(changed.throwError()).isFalse();
            assertThat(original.pathUrl()).isEqualTo("/orders");
            assertThat(original.throwError()).isTrue();
        }
    }
}
