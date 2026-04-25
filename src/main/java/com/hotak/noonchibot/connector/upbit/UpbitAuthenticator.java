package com.hotak.noonchibot.connector.upbit;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTCreator;
import com.auth0.jwt.algorithms.Algorithm;
import com.hotak.noonchibot.connector.web.Authenticator;
import com.hotak.noonchibot.connector.web.RestRequest;
import com.hotak.noonchibot.connector.web.WsRequest;
import org.springframework.http.HttpHeaders;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

public class UpbitAuthenticator implements Authenticator {
    private final String apiKey;
    private final Algorithm algorithm;

    public UpbitAuthenticator(String apiKey, String secretKey) {
        this.apiKey = apiKey;
        this.algorithm = Algorithm.HMAC512(secretKey);
    }

    @Override
    public RestRequest restAuthenticate(RestRequest restRequest) {
        Map<String, Object> hashTarget = resolveHashTarget(restRequest);

        String jwtToken = generateJwtToken(hashTarget);
        HttpHeaders headers = new HttpHeaders(restRequest.headers());
        headers.add("Authorization", "Bearer " + jwtToken);
        return restRequest.toBuilder().headers(headers).build();
    }

    @Override
    public WsRequest wsAuthenticate(WsRequest wsRequest) { return wsRequest; }

    @SuppressWarnings("unchecked")
    private Map<String, Object> resolveHashTarget(RestRequest restRequest) {
        // body가 Map이면 우선 사용 (POST/PUT)
        if (restRequest.body() instanceof Map<?, ?> bodyMap && !bodyMap.isEmpty()) {
            return (Map<String, Object>) bodyMap;
        }
        // body가 없거나 Map이 아니면 params 사용 (GET/DELETE)
        Map<String, Object> params = restRequest.params();
        if (params != null && !params.isEmpty()) {
            return params;
        }
        return null;
    }

    private String generateJwtToken(Map<String, Object> params) {
        JWTCreator.Builder builder = JWT.create()
                .withClaim("access_key", apiKey)
                .withClaim("nonce", UUID.randomUUID().toString());

        if (params != null && !params.isEmpty()) {
            String queryString = encode(params);
            String queryHash = sha512(queryString);
            builder.withClaim("query_hash", queryHash)
                    .withClaim("query_hash_alg", "SHA512");
        }
        return builder.sign(algorithm);
    }

    private String encode(Map<String, Object> params) {
        return params.entrySet().stream()
                .map(e -> e.getKey() + "=" + toStringValue(e.getValue()))
                .collect(Collectors.joining("&"));
    }

    private String toStringValue(Object value) {
        if (value instanceof String s) return s;
        if (value instanceof BigDecimal bd) return bd.toPlainString();
        if (value instanceof Number n) return n.toString();
        if (value instanceof Boolean b) return b.toString();
        throw new IllegalArgumentException("Unsupported param type: " + value.getClass().getSimpleName());
    }

    private String sha512(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-512");
            byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-512 not available", e);
        }
    }
}