package com.hotak.noonchibot.connector.binance;

import com.hotak.noonchibot.connector.web.RestRequest;
import com.hotak.noonchibot.connector.web.TimeSynchronizer;
import com.hotak.noonchibot.connector.web.WsRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import tools.jackson.databind.ObjectMapper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class BinanceAuthenticatorTest {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private BinanceAuthenticator authenticator;

    @BeforeEach
    void setUp() {
        TimeSynchronizer timeSynchronizer = mock(TimeSynchronizer.class);
        when(timeSynchronizer.serverTime()).thenReturn(1700000000000L);
        authenticator = new BinanceAuthenticator(
                "testApiKey",
                "testSecretKey",
                timeSynchronizer,
                objectMapper
        );
    }

    @Test
    @DisplayName("REST 인증 시 timestamp와 signature 파라미터가 추가된다")
    void restAuthenticate_addsTimestampAndSignature() {
        var request = RestRequest.builder()
                .method(HttpMethod.GET)
                .pathUrl("/")
                .params(Map.of("symbol", "BTCUSDT"))
                .headers(new HttpHeaders())
                .build();

        var result = authenticator.restAuthenticate(request);

        assertThat(result.params()).containsKeys("timestamp", "signature");
        assertThat(result.params().get("symbol")).isEqualTo("BTCUSDT");
    }

    @Test
    @DisplayName("REST 인증 시 X-MBX-APIKEY 헤더가 추가된다")
    void restAuthenticate_addsApiKeyHeader() {
        var request = RestRequest.builder()
                .method(HttpMethod.GET)
                .pathUrl("/")
                .params(Map.of())
                .headers(new HttpHeaders())
                .build();

        var result = authenticator.restAuthenticate(request);

        assertThat(result.headers().getFirst("X-MBX-APIKEY")).isEqualTo("testApiKey");
    }

    @Test
    @DisplayName("동일한 요청에 대해 항상 같은 signature가 생성된다")
    void signatureIsConsistent() {
        var request = RestRequest.builder()
                .method(HttpMethod.GET)
                .pathUrl("/")
                .params(Map.of("symbol", "BTCUSDT"))
                .headers(new HttpHeaders())
                .build();

        var result1 = authenticator.restAuthenticate(request);
        var result2 = authenticator.restAuthenticate(request);

        assertThat(result1.params().get("signature")).isEqualTo(result2.params().get("signature"));
    }

    @Test
    @DisplayName("HMAC-SHA256으로 생성된 signature가 기대값과 일치한다")
    void signatureMatchesExpected() {
        var request = RestRequest.builder()
                .method(HttpMethod.GET)
                .pathUrl("/")
                .params(Map.of("symbol", "BTCUSDT"))
                .headers(new HttpHeaders())
                .build();

        var result = authenticator.restAuthenticate(request);

        String queryString = "symbol=BTCUSDT&timestamp=1700000000000";
        String expected = hmacSha256("testSecretKey", queryString);
        assertThat(result.params().get("signature")).isEqualTo(expected);
    }

    @Test
    @DisplayName("WebSocket 인증은 요청을 그대로 통과시킨다")
    void wsAuthenticate_returnsPassthrough() {
        var wsRequest = mock(WsRequest.class);
        assertThat(authenticator.wsAuthenticate(wsRequest)).isSameAs(wsRequest);
    }

    private static String hmacSha256(String key, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
