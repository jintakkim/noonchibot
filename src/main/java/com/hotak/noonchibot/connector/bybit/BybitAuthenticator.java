package com.hotak.noonchibot.connector.bybit;

import com.hotak.noonchibot.connector.web.Authenticator;
import com.hotak.noonchibot.connector.web.RestRequest;
import com.hotak.noonchibot.connector.web.TimeSynchronizer;
import com.hotak.noonchibot.connector.web.WsRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import tools.jackson.databind.ObjectMapper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.stream.Collectors;

public class BybitAuthenticator implements Authenticator {
    private final String apiKey;
    private final Mac mac;
    private final TimeSynchronizer timeSynchronizer;
    private final ObjectMapper objectMapper;
    private final String recvWindow = "5000"; // 요청 유효시간

    public BybitAuthenticator(String apiKey, String secretKey, TimeSynchronizer timeSynchronizer, ObjectMapper objectMapper) {
        this.apiKey = apiKey;
        this.timeSynchronizer = timeSynchronizer;
        this.objectMapper = objectMapper;
        try {
            this.mac = Mac.getInstance("HmacSHA256");
            this.mac.init(new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalArgumentException("Invalid secret key", e);
        }
    }

    @Override
    public RestRequest restAuthenticate(RestRequest restRequest) {
        long timestamp = timeSynchronizer.serverTime();
        // GET 요청은 서명 대상 쿼리 문자열을 키 기준 알파벳 정렬해서 만든다.
        // 실제 URL 로 나가는 params 도 동일한 순서여야 서명과 일치하므로,
        // 여기서 params 자체를 정렬된 TreeMap 으로 교체해서 반환한다.
        // (HashMap 등으로 오면 iteration 순서가 non-deterministic 이라 서명이 깨짐)
        RestRequest requestWithSortedParams = restRequest;
        if (restRequest.method() == HttpMethod.GET
                && restRequest.params() != null
                && !restRequest.params().isEmpty()) {
            requestWithSortedParams = restRequest.toBuilder()
                    .params(new TreeMap<>(restRequest.params()))
                    .build();
        }
        String payload = createPayload(requestWithSortedParams);
        String rawData = timestamp + apiKey + recvWindow + payload;
        String signature = generateSignature(rawData);
        HttpHeaders headers = new HttpHeaders(requestWithSortedParams.headers());
        headers.add("X-BAPI-API-KEY", apiKey);
        headers.add("X-BAPI-SIGN", signature);
        headers.add("X-BAPI-TIMESTAMP", String.valueOf(timestamp));
        headers.add("X-BAPI-RECV-WINDOW", recvWindow);
        return requestWithSortedParams.toBuilder().headers(headers).build();
    }

    @Override
    public WsRequest wsAuthenticate(WsRequest wsRequest) { return wsRequest; }

    public Map<String, Object> generateWsAuthParams() {
        long expires = timeSynchronizer.serverTime() + 5000;
        String rawData = "GET/realtime" + expires;
        String signature = generateSignature(rawData);

        return Map.of(
                "op", "auth",
                "args", List.of(apiKey, expires, signature)
        );
    }

    /**
     * 바이비트 V5 서명 공식:
     * signature = HMAC_SHA256(secret, timestamp + api_key + recv_window + payload)
     */
    private String generateSignature(String data) {
        byte[] digest = getMac().doFinal(data.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(digest);
    }

    // Bybit은 주문 타입에 따라 body가 다르다
    private String createPayload(RestRequest restRequest) {
        if (restRequest.method() == HttpMethod.GET) {
            return encode(restRequest.params());
        } else {
            return encodeJsonBody(restRequest.body());
        }
    }

    private String encode(Map<String, Object> params) {
        if (params == null || params.isEmpty()) return "";
        return new TreeMap<>(params).entrySet().stream()
                .map(e -> e.getKey() + "=" + e.getValue())
                .collect(Collectors.joining("&"));
    }

    private String encodeJsonBody(Object body) {
        if (body == null) return "";
        try {
            return objectMapper.writeValueAsString(body);
        } catch (Exception e) {
            return "";
        }
    }

    private Mac getMac() {
        try {
            return (Mac) mac.clone();
        } catch (CloneNotSupportedException e) {
            throw new IllegalStateException(e);
        }
    }
}