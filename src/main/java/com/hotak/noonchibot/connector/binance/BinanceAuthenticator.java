package com.hotak.noonchibot.connector.binance;

import com.hotak.noonchibot.connector.web.Authenticator;
import com.hotak.noonchibot.connector.web.RestRequest;
import com.hotak.noonchibot.connector.web.TimeSynchronizer;
import com.hotak.noonchibot.connector.web.WsRequest;
import org.springframework.http.HttpHeaders;
import tools.jackson.databind.ObjectMapper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.stream.Collectors;

class BinanceAuthenticator implements Authenticator {
    private final String apiKey;
    private final Mac mac;
    private final TimeSynchronizer timeSynchronizer;
    private final ObjectMapper objectMapper;

    public BinanceAuthenticator(String apiKey, String secretKey, TimeSynchronizer timeSynchronizer, ObjectMapper objectMapper) {
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
        Map<String, Object> authParams = addAuthToParams(restRequest.params());
        HttpHeaders headers = new HttpHeaders(restRequest.headers());
        headers.add("X-MBX-APIKEY", apiKey);
        return restRequest.toBuilder().params(authParams).headers(headers).build();
    }

    @Override
    public WsRequest wsAuthenticate(WsRequest wsRequest) {
        return wsRequest; //binance는 별도로 인증 진행
    }

    /**
     * WebSocket API 유저 스트림 구독용 파라미터 생성
     * params를 알파벳 순 정렬, URL 인코딩 안 함, apiKey가 서명 대상에 포함
     */
    public Map<String, Object> generateWsSubscribeParams() {
        long timestamp = timeSynchronizer.serverTime();
        Map<String, Object> params = new TreeMap<>();  // 알파벳 순 정렬
        params.put("apiKey", apiKey);
        params.put("timestamp", timestamp);
        params.put("signature", generateWsSignature(params));
        return params;
    }

    private String generateWsSignature(Map<String, Object> params) {
        String payload = params.entrySet().stream()
                .map(e -> e.getKey() + "=" + e.getValue())
                .collect(Collectors.joining("&"));
        byte[] digest = getMac().doFinal(payload.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(digest);
    }

    private Map<String, Object> addAuthToParams(Map<String, Object> params) {
        var signed = new LinkedHashMap<>(params != null ? params : Map.of());
        signed.put("timestamp", timeSynchronizer.serverTime());
        signed.put("signature", generateSignature(signed));
        return signed;
    }

    private String generateSignature(Map<String, Object> params)  {
        String queryString = encode(params);
        byte[] digest = getMac().doFinal(queryString.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(digest);
    }

    private String encode(Map<String, Object> params) {
        return params.entrySet().stream()
                .map(e -> e.getKey() + "=" + toStringValue(e.getValue()))
                .collect(Collectors.joining("&"));
    }

    private String toStringValue(Object value) {
        if (value instanceof String s) return s;
        if (value instanceof Number n) return n.toString();
        if (value instanceof List<?> list) return objectMapper.writeValueAsString(list);
        throw new IllegalArgumentException("Unsupported param type: " + value.getClass().getSimpleName());
    }

    private Mac getMac() {
        try {
            return (Mac) mac.clone();
        } catch (CloneNotSupportedException e) {
            throw new IllegalStateException(e);
        }
    }
}
