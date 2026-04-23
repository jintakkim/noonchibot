package com.hotak.noonchibot.connector.bybit;

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
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class BybitAuthenticatorTest {
    private static final String API_KEY = "testApiKey";
    private static final String SECRET_KEY = "testSecretKey";
    private static final String RECV_WINDOW = "5000";
    private static final long TIMESTAMP = 1700000000000L;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private BybitAuthenticator authenticator;

    @BeforeEach
    void setUp() {
        TimeSynchronizer timeSynchronizer = mock(TimeSynchronizer.class);
        when(timeSynchronizer.serverTime()).thenReturn(TIMESTAMP);
        authenticator = new BybitAuthenticator(
                API_KEY,
                SECRET_KEY,
                timeSynchronizer,
                objectMapper
        );
    }

    @Test
    @DisplayName("REST 인증 시 Bybit v5 헤더 네 개가 추가된다")
    void restAuthenticate_addsBybitHeaders() {
        var request = RestRequest.builder()
                .method(HttpMethod.GET)
                .pathUrl("/")
                .params(Map.of("symbol", "BTCUSDT"))
                .headers(new HttpHeaders())
                .build();

        var result = authenticator.restAuthenticate(request);

        assertThat(result.headers().getFirst("X-BAPI-API-KEY")).isEqualTo(API_KEY);
        assertThat(result.headers().getFirst("X-BAPI-SIGN")).isNotBlank();
        assertThat(result.headers().getFirst("X-BAPI-TIMESTAMP")).isEqualTo(String.valueOf(TIMESTAMP));
        assertThat(result.headers().getFirst("X-BAPI-RECV-WINDOW")).isEqualTo(RECV_WINDOW);
    }

    @Test
    @DisplayName("REST 인증은 params를 변경하지 않는다 (Bybit는 서명 정보를 headers에만 넣는다)")
    void restAuthenticate_preservesParams() {
        var request = RestRequest.builder()
                .method(HttpMethod.GET)
                .pathUrl("/")
                .params(Map.of("symbol", "BTCUSDT", "category", "spot"))
                .headers(new HttpHeaders())
                .build();

        var result = authenticator.restAuthenticate(request);

        assertThat(result.params()).containsExactlyInAnyOrderEntriesOf(
                Map.of("symbol", "BTCUSDT", "category", "spot")
        );
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

        assertThat(result1.headers().getFirst("X-BAPI-SIGN"))
                .isEqualTo(result2.headers().getFirst("X-BAPI-SIGN"));
    }

    @Test
    @DisplayName("GET 요청 signature는 HMAC_SHA256(timestamp + apiKey + recvWindow + 정렬된 queryString)와 일치한다")
    void signatureMatchesExpectedForGet() {
        var request = RestRequest.builder()
                .method(HttpMethod.GET)
                .pathUrl("/")
                .params(Map.of("symbol", "BTCUSDT", "category", "spot"))
                .headers(new HttpHeaders())
                .build();

        var result = authenticator.restAuthenticate(request);

        // 파라미터는 키 기준 알파벳 정렬 (TreeMap)
        String queryString = "category=spot&symbol=BTCUSDT";
        String rawData = TIMESTAMP + API_KEY + RECV_WINDOW + queryString;
        String expected = hmacSha256(SECRET_KEY, rawData);
        assertThat(result.headers().getFirst("X-BAPI-SIGN")).isEqualTo(expected);
    }

    @Test
    @DisplayName("POST 요청 signature는 HMAC_SHA256(timestamp + apiKey + recvWindow + jsonBody)와 일치한다")
    void signatureMatchesExpectedForPost() throws Exception {
        Map<String, Object> body = Map.of("symbol", "BTCUSDT", "side", "Buy");
        var request = RestRequest.builder()
                .method(HttpMethod.POST)
                .pathUrl("/")
                .body(body)
                .headers(new HttpHeaders())
                .build();

        var result = authenticator.restAuthenticate(request);

        String jsonBody = objectMapper.writeValueAsString(body);
        String rawData = TIMESTAMP + API_KEY + RECV_WINDOW + jsonBody;
        String expected = hmacSha256(SECRET_KEY, rawData);
        assertThat(result.headers().getFirst("X-BAPI-SIGN")).isEqualTo(expected);
    }

    @Test
    @DisplayName("WebSocket 인증은 요청을 그대로 통과시킨다")
    void wsAuthenticate_returnsPassthrough() {
        var wsRequest = mock(WsRequest.class);
        assertThat(authenticator.wsAuthenticate(wsRequest)).isSameAs(wsRequest);
    }

    @Test
    @DisplayName("generateWsAuthParams는 {op:auth, args:[apiKey, expires, signature]} 구조를 반환한다")
    void generateWsAuthParams_hasCorrectStructure() {
        Map<String, Object> params = authenticator.generateWsAuthParams();

        assertThat(params).containsEntry("op", "auth");

        @SuppressWarnings("unchecked")
        List<Object> args = (List<Object>) params.get("args");
        assertThat(args).hasSize(3);
        assertThat(args.get(0)).isEqualTo(API_KEY);

        long expires = (Long) args.get(1);
        assertThat(expires).isEqualTo(TIMESTAMP + 10000L);

        String expectedSignature = hmacSha256(SECRET_KEY, "GET/realtime" + expires);
        assertThat(args.get(2)).isEqualTo(expectedSignature);
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
