package com.hotak.noonchibot.connector.hyperliquid;

import com.hotak.noonchibot.connector.web.RestAssistantImpl;
import com.hotak.noonchibot.connector.web.RestRequest;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpMethod;
import org.web3j.crypto.*;
import org.web3j.utils.Numeric;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Hyperliquid named agent (API wallet) 생성 + 등록 도구.
 *
 * 일회성 administrative 작업용
 * 운영 봇 lifecycle과 분리해서 사용
 * Master private key가 메모리에 일시 상주하므로 호출 후 즉시 폐기 권장.
 *
 * Named agent는 영구 유효 (master가 명시적 deregister하기 전까지).
 * 같은 이름으로 재등록하면 기존 키가 무효화되고 새 키로 회전.
 * 다른 이름의 agent들은 영향받지 않음.
 */
@Slf4j
@RequiredArgsConstructor
public class HyperliquidAgentGenerator {

    private final RestAssistantImpl restAssistant;
    private final ObjectMapper objectMapper;
    private final boolean isMainnet;

    /**
     * 새 keypair 생성 + master로 approveAgent 서명 + Hyperliquid에 등록.
     *
     * @param masterPrivateKey master wallet의 private key (등록 후 호출자가 폐기)
     * @param agentName named agent의 이름 (같은 이름은 기존 agent 회전)
     * @return 새로 등록된 agent 정보
     */
    public GeneratedAgent generateAndRegister(String masterPrivateKey, String agentName) {
        if (agentName == null || agentName.isBlank()) {
            throw new IllegalArgumentException("agentName must not be blank (unnamed agent is not recommended)");
        }
        // 새 agent keypair 생성
        ECKeyPair agentKeyPair = createKeyPair();
        String agentPrivateKey = "0x" + Numeric.toHexStringNoPrefixZeroPadded(agentKeyPair.getPrivateKey(), 64);
        String agentAddress = Keys.toChecksumAddress("0x" + Keys.getAddress(agentKeyPair.getPublicKey()));

        log.info("Generated new agent: address={}, name={}", agentAddress, agentName);

        // master로 approveAgent 액션 서명
        ECKeyPair masterKeyPair = ECKeyPair.create(Numeric.toBigInt(masterPrivateKey));
        long nonce = System.currentTimeMillis();

        Map<String, Object> action = buildApproveAgentAction(agentAddress, agentName, nonce);
        Map<String, Object> signature = signApproveAgent(masterKeyPair, action);

        // 3. Hyperliquid에 전송
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("action", action);
        payload.put("nonce", nonce);
        payload.put("signature", signature);

        JsonNode response = restAssistant.executeRequestAndGetJsonBody(
                RestRequest.builder()
                        .method(HttpMethod.POST)
                        .pathUrl("/exchange")
                        .body(payload)
                        .build()
        );

        String status = response.has("status") ? response.get("status").asString() : "unknown";
        if (!"ok".equals(status)) {
            throw new IllegalStateException("Hyperliquid agent registration failed: " + response);
        }
        log.info("Agent registered successfully: address={}, name={}", agentAddress, agentName);
        return new GeneratedAgent(agentAddress, agentPrivateKey, agentName);
    }

    /**
     * 새 keypair만 생성 (등록 안 함).
     * 미리 키만 만들고 사이트에서 수동 등록할 때 사용.
     */
    public GeneratedAgent generateKeyPairOnly(String agentName) {
        ECKeyPair keyPair = createKeyPair();
        String privateKey = "0x" + Numeric.toHexStringNoPrefixZeroPadded(keyPair.getPrivateKey(), 64);
        String address = Keys.toChecksumAddress("0x" + Keys.getAddress(keyPair.getPublicKey()));
        return new GeneratedAgent(address, privateKey, agentName);
    }

    private ECKeyPair createKeyPair() {
        try {
            return Keys.createEcKeyPair(new SecureRandom());
        } catch (Exception e) {
            throw new IllegalStateException("Failed to create keypair", e);
        }
    }

    private Map<String, Object> buildApproveAgentAction(
            String agentAddress, String agentName, long nonce) {
        Map<String, Object> action = new LinkedHashMap<>();
        action.put("type", "approveAgent");
        action.put("hyperliquidChain", isMainnet ? "Mainnet" : "Testnet");
        action.put("signatureChainId", isMainnet ? "0xa4b1" : "0x66eee");
        action.put("agentAddress", agentAddress.toLowerCase());
        action.put("agentName", agentName);
        action.put("nonce", nonce);
        return action;
    }

    /**
     * User-signed action 서명.
     * L1 action과 달리 phantom agent 없이 직접 EIP-712 서명.
     */
    private Map<String, Object> signApproveAgent(ECKeyPair masterKeyPair, Map<String, Object> action) {
        try {
            Map<String, Object> typedData = buildApproveAgentTypedData(action);
            String json = objectMapper.writeValueAsString(typedData);
            StructuredDataEncoder encoder = new StructuredDataEncoder(json);

            byte[] hash = encoder.hashStructuredData();
            Sign.SignatureData sig = Sign.signMessage(hash, masterKeyPair, false);

            Map<String, Object> signature = new LinkedHashMap<>();
            signature.put("r", "0x" + Numeric.toHexStringNoPrefixZeroPadded(
                    new BigInteger(1, sig.getR()), 64));
            signature.put("s", "0x" + Numeric.toHexStringNoPrefixZeroPadded(
                    new BigInteger(1, sig.getS()), 64));
            signature.put("v", sig.getV()[0] & 0xFF);
            return signature;

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

        private Map<String, Object> buildApproveAgentTypedData(Map<String, Object> action) {
        Map<String, Object> typedData = new LinkedHashMap<>();

        Map<String, Object> domain = new LinkedHashMap<>();
        domain.put("name", "HyperliquidSignTransaction");
        domain.put("version", "1");
        domain.put("chainId", isMainnet ? 42161 : 421614);
        domain.put("verifyingContract", "0x0000000000000000000000000000000000000000");
        typedData.put("domain", domain);

        Map<String, Object> types = new LinkedHashMap<>();
        types.put("EIP712Domain", List.of(
                Map.of("name", "name", "type", "string"),
                Map.of("name", "version", "type", "string"),
                Map.of("name", "chainId", "type", "uint256"),
                Map.of("name", "verifyingContract", "type", "address")
        ));
        types.put("HyperliquidTransaction:ApproveAgent", List.of(
                Map.of("name", "hyperliquidChain", "type", "string"),
                Map.of("name", "agentAddress", "type", "address"),
                Map.of("name", "agentName", "type", "string"),
                Map.of("name", "nonce", "type", "uint64")
        ));
        typedData.put("types", types);
        typedData.put("primaryType", "HyperliquidTransaction:ApproveAgent");
        typedData.put("message", action);

        return typedData;
    }

    @Getter
    @RequiredArgsConstructor
    public static class GeneratedAgent {
        private final String address;
        private final String privateKey;
        private final String name;

        @Override
        public String toString() {
            return "GeneratedAgent{address=" + address + ", name=" + name + ", privateKey=<redacted>}";
        }
    }
}